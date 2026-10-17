package com.example.util.simpletimetracker.data_local.record

import androidx.room.withTransaction
import com.example.util.simpletimetracker.domain.record.model.operationIdentity
import com.example.util.simpletimetracker.data_local.planning.*
import com.example.util.simpletimetracker.domain.planning.*
import com.example.util.simpletimetracker.data_local.database.AppDatabase
import com.example.util.simpletimetracker.data_local.recordTag.RecordToRecordTagDataLocalMapper
import com.example.util.simpletimetracker.data_local.recordTag.RunningRecordToRecordTagDataLocalMapper
import com.example.util.simpletimetracker.domain.base.CurrentTimestampProvider
import com.example.util.simpletimetracker.domain.record.interactor.RecordTimeValidator
import com.example.util.simpletimetracker.domain.record.model.Record
import com.example.util.simpletimetracker.domain.record.model.RecordBase
import com.example.util.simpletimetracker.domain.record.model.RecordMutationError
import com.example.util.simpletimetracker.domain.record.model.RecordMutationException
import com.example.util.simpletimetracker.domain.record.model.RunningRecord
import com.example.util.simpletimetracker.domain.record.model.RecordPhoto
import com.example.util.simpletimetracker.data_local.recordPhoto.RecordPhotoDBO
import com.example.util.simpletimetracker.domain.record.repo.RecordMutationRepo
import com.example.util.simpletimetracker.domain.record.repo.RecordRepo
import com.example.util.simpletimetracker.domain.record.repo.RunningRecordRepo
import com.example.util.simpletimetracker.domain.record.repo.TimerMutation
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

@Singleton
class RecordMutationRepoImpl @Inject constructor(
    private val database: AppDatabase,
    private val records: RecordRepo,
    private val running: RunningRecordRepo,
    private val recordMapper: RecordDataLocalMapper,
    private val runningMapper: RunningRecordDataLocalMapper,
    private val tagMapper: RecordToRecordTagDataLocalMapper,
    private val runningTagMapper: RunningRecordToRecordTagDataLocalMapper,
    private val validator: RecordTimeValidator,
    private val clock: CurrentTimestampProvider,
    private val restoreGate: com.example.util.simpletimetracker.domain.backup.interactor.DataRestoreGate = com.example.util.simpletimetracker.domain.backup.interactor.DataRestoreGate(),
) : RecordMutationRepo {

    override suspend fun saveDetails(record: Record, photos: List<RecordPhoto>): Long = mutate {
        if (record.id != 0L) requireRecord(record.id)
        validate(listOf(record), setOf(record.id))
        val id = write(record)
        writePhotos(id, false, photos)
        PlanLinks.clip(database, id, record.timeStarted, record.timeEnded)
        id
    }

    override suspend fun editRunningDetails(originalId: Long, record: RunningRecord, photos: List<RecordPhoto>) = mutate {
        editRunningInTransaction(originalId, record)
        writePhotos(record.id, true, photos)
    }

    override suspend fun save(records: List<Record>): List<Long> = mutate {
        validate(records, records.map { it.id }.toSet())
        records.map { record -> write(record).also { PlanLinks.clip(database, it, record.timeStarted, record.timeEnded) } }
    }

    override suspend fun start(record: RunningRecord, now: Long, expectedSessionId: String?, allowFocusExit: Boolean, plan: PlanOccurrence?): TimerMutation = mutate {
        if (plan != null) {
            val schedules = database.planningDao().planSchedules().map { PlanningJson.schedule(it.contentJson) }
            val exceptions = database.planningDao().planExceptions().map { PlanningJson.exception(it.contentJson) }
            require(PlanCalendar.occurrences(schedules, exceptions, plan.start, plan.end).any { it.id == plan.id && it.status == PlanStatus.OPEN && it.schedule.kind != PlanKind.BUFFER })
        }
        val identity = database.lifeClockSettingsDao().get()
        val ratingIds = identity?.let { setOf(it.happinessTagId, it.meaningTagId) }.orEmpty()
        val policy = record.templateId?.let { database.recordShortcutDao().get(it)?.shortcut?.timerPolicy }
            ?.let(com.example.util.simpletimetracker.data_local.planning.PlanningJson::policy)
            ?: com.example.util.simpletimetracker.data_local.planning.PlanningJson.policy(database.recordTypeDao().get(record.id)?.timerPolicy)
            ?: com.example.util.simpletimetracker.domain.rhythm.TimerPolicy()
        val started = record.copy(tags = record.tags.filterNot { it.tagId in ratingIds },
            timerSession = com.example.util.simpletimetracker.domain.rhythm.TimerSession(startedAt = now, policy = policy))
        val active = active()
        if (active.size > 1) fail(RecordMutationError.MULTIPLE_RUNNING)
        val current = active.singleOrNull()
        if (current?.id == record.id && (record.templateId == null || current.templateId == record.templateId)) {
            return@mutate TimerMutation(emptyList(), null)
        }
        checkExit(current, now, expectedSessionId, allowFocusExit)
        validateRunning(started, now)
        val ended = active.map { ended(it, record.timeStarted) }
        validate(ended, emptySet(), active.map { it.id }.toSet(), now)
        ended.zip(active).forEach { (finished, previous) ->
            val id = write(finished)
            database.recordPhotoDao().transferToRecord(previous.id, id)
            PlanLinks.finish(database, previous.id, id, finished.timeStarted, finished.timeEnded)
            deleteRunning(previous.id)
        }
        writeRunning(started)
        if (plan != null) {
            val link = PlanRecordLink(runningRecordId = started.id, occurrenceId = plan.id, caption = plan.schedule.title, timeStarted = started.timeStarted)
            database.planningDao().save(PlanRecordLinkDBO(link.id, PlanningJson.encode(link)))
        }
        TimerMutation(active, started)
    }

    override suspend fun stop(typeId: Long, now: Long, expectedSessionId: String?, allowFocusExit: Boolean): TimerMutation = mutate {
        val record = database.runningRecordDao().get(typeId)?.let(runningMapper::map)
            ?: return@mutate TimerMutation(emptyList(), null)
        checkExit(record, now, expectedSessionId, allowFocusExit)
        val finished = ended(record, now)
        validate(listOf(finished), emptySet(), setOf(typeId), now)
        val id = write(finished)
        database.recordPhotoDao().transferToRecord(typeId, id)
        PlanLinks.finish(database, typeId, id, finished.timeStarted, finished.timeEnded)
        deleteRunning(typeId)
        TimerMutation(listOf(record), null)
    }

    override suspend fun muteReminder(sessionId: String): Unit = mutate {
        val current = active().singleOrNull() ?: return@mutate
        val session = current.timerSession ?: return@mutate
        if (session.id == sessionId) writeRunning(current.copy(timerSession = session.copy(muted = true)))
    }

    override suspend fun editRunning(originalId: Long, record: RunningRecord) = mutate {
        editRunningInTransaction(originalId, record)
    }

    private fun checkExit(record: RunningRecord?, now: Long, expected: String?, allowed: Boolean) {
        val session = record?.timerSession
        if (expected != null && expected != (record?.operationIdentity() ?: "idle")) fail(RecordMutationError.NOT_FOUND)
        if (session != null && session.policy.needsExitConfirmation(session.startedAt, now) && (!allowed || expected != session.id)) {
            fail(RecordMutationError.FOCUS_CONFIRMATION_REQUIRED)
        }
    }

    private suspend fun editRunningInTransaction(originalId: Long, record: RunningRecord) {
        if (database.runningRecordDao().get(originalId) == null) fail(RecordMutationError.NOT_FOUND)
        if (active().any { it.id != originalId }) fail(RecordMutationError.MULTIPLE_RUNNING)
        validateRunning(record, clock.get())
        val session = database.runningRecordDao().get(originalId)?.let(runningMapper::map)?.timerSession
        writeRunning(record.copy(timerSession = session))
        PlanLinks.moveRunning(database, originalId, record.id, record.timeStarted)
        if (originalId != record.id) {
            database.recordPhotoDao().transferRunning(originalId, record.id)
            deleteRunning(originalId)
        }
    }

    override suspend fun merge(record: Record, previousId: Long, tags: List<RecordBase.Tag>?, photos: List<RecordPhoto>?, recoveryResolved: Boolean) = mutate {
        val previous = requireRecord(previousId)
        val original = requireRecord(record.id)
        if (previous.id == original.id || previous.timeStarted >= original.timeStarted) fail(RecordMutationError.GAP)
        if (previous.timeEnded < original.timeStarted || previous.timeEnded < record.timeStarted) fail(RecordMutationError.GAP)
        if (!recoveryResolved && previous.recovery != null && record.recovery != null && previous.recovery != record.recovery) fail(RecordMutationError.TAG_CONFLICT)
        val chosen = tags ?: mergeTags(previous.tags, record.tags)
        val merged = record.copy(
            timeStarted = previous.timeStarted,
            comment = listOf(previous.comment, record.comment).filter { it.isNotBlank() }.distinct().joinToString("\n"),
            summary = listOf(previous.summary, record.summary).filter { it.isNotBlank() }.distinct().joinToString("＋"),
            tags = chosen,
            recovery = if (recoveryResolved) record.recovery else record.recovery ?: previous.recovery,
            reviewState = validator.state(previous.timeStarted, record.timeEnded),
        )
        validate(listOf(merged), setOf(record.id, previousId))
        write(merged)
        photos?.let { writePhotos(record.id, false, it) }
        database.recordPhotoDao().transferRecord(previousId, record.id)
        database.recordToRecordTagDao().deleteAllByRecordId(previousId)
        PlanLinks.merge(database, previousId, record.id)
        PlanLinks.clip(database, record.id, merged.timeStarted, merged.timeEnded)
        database.recordDao().delete(previousId)
    }

    override suspend fun split(record: Record, boundary: Long, attachmentsBefore: Boolean, photos: List<RecordPhoto>?) = mutate {
        requireRecord(record.id)
        if (boundary <= record.timeStarted || boundary >= record.timeEnded) fail(RecordMutationError.EMPTY_INTERVAL)
        val plain = record.tags.map { it.copy(numericValue = null, ratedAt = null) }
        val before = record.copy(
            id = 0, timeEnded = boundary, tags = if (attachmentsBefore) record.tags else plain,
            recovery = if (attachmentsBefore) record.recovery else null,
            reviewState = validator.state(record.timeStarted, boundary),
        )
        val after = record.copy(
            timeStarted = boundary, tags = if (attachmentsBefore) plain else record.tags,
            recovery = if (attachmentsBefore) null else record.recovery,
            reviewState = validator.state(boundary, record.timeEnded),
        )
        validate(listOf(before, after), setOf(record.id))
        write(after)
        photos?.let { writePhotos(record.id, false, it) }
        val beforeId = write(before)
        PlanLinks.split(database, record.id, beforeId, boundary)
        PlanLinks.clip(database, beforeId, before.timeStarted, before.timeEnded)
        PlanLinks.clip(database, record.id, after.timeStarted, after.timeEnded)
        if (attachmentsBefore) database.recordPhotoDao().transferRecord(record.id, beforeId)
    }

    override suspend fun adjustBoundary(record: Record, neighborId: Long, boundary: Long, previous: Boolean, neighborRunning: Boolean, photos: List<RecordPhoto>?): Unit = mutate {
        val original = requireRecord(record.id)
        if (neighborRunning) {
            if (previous) fail(RecordMutationError.GAP)
            val active = active()
            val next = active.singleOrNull()?.takeIf { it.id == neighborId } ?: fail(RecordMutationError.MULTIPLE_RUNNING)
            if (next.timeStarted > original.timeEnded) fail(RecordMutationError.GAP)
            val changed = record.copy(timeEnded = boundary, reviewState = validator.state(record.timeStarted, boundary))
            validate(listOf(changed), setOf(record.id), setOf(next.id))
            val changedRunning = next.copy(timeStarted = boundary)
            validateRunning(changedRunning, clock.get(), setOf(record.id))
            write(changed)
            PlanLinks.clip(database, changed.id, changed.timeStarted, changed.timeEnded)
            writeRunning(changedRunning)
            PlanLinks.moveRunning(database, next.id, next.id, changedRunning.timeStarted)
            photos?.let { writePhotos(record.id, false, it) }
            return@mutate
        }
        val neighbor = requireRecord(neighborId)
        if (neighbor.id == record.id) fail(RecordMutationError.GAP)
        val changed = if (previous) {
            if (neighbor.timeStarted >= original.timeStarted || neighbor.timeEnded < original.timeStarted) fail(RecordMutationError.GAP)
            listOf(
                neighbor.copy(timeEnded = boundary, reviewState = validator.state(neighbor.timeStarted, boundary)),
                record.copy(timeStarted = boundary, reviewState = validator.state(boundary, record.timeEnded)),
            )
        } else {
            if (neighbor.timeStarted < original.timeStarted || neighbor.timeStarted > original.timeEnded) fail(RecordMutationError.GAP)
            listOf(
                record.copy(timeEnded = boundary, reviewState = validator.state(record.timeStarted, boundary)),
                neighbor.copy(timeStarted = boundary, reviewState = validator.state(boundary, neighbor.timeEnded)),
            )
        }
        validate(changed, setOf(record.id, neighborId))
        changed.forEach { write(it); PlanLinks.clip(database, it.id, it.timeStarted, it.timeEnded) }
        photos?.let { writePhotos(record.id, false, it) }
    }

    private suspend fun <T> mutate(block: suspend () -> T): T {
        val token = restoreGate.token()
        return withContext(Dispatchers.IO) {
            try {
                database.withTransaction {
                    if (!restoreGate.canWrite(token)) fail(RecordMutationError.DATA_RESTORING)
                    block()
                }
            } finally {
                withContext(NonCancellable) {
                    records.invalidateCache()
                    running.invalidateCache()
                }
            }
        }
    }

    private suspend fun allRecords(): List<Record> = database.recordDao().getAll().map(recordMapper::map)
    private suspend fun active(): List<RunningRecord> = database.runningRecordDao().getAll().map(runningMapper::map)
    private suspend fun requireRecord(id: Long): Record = database.recordDao().get(id)?.let(recordMapper::map)
        ?: fail(RecordMutationError.NOT_FOUND)

    private suspend fun validate(proposed: List<Record>, ignored: Set<Long>, ignoredRunning: Set<Long> = emptySet(), now: Long = clock.get()) {
        if (proposed.filter { it.id != 0L }.map { it.id }.distinct().size != proposed.count { it.id != 0L }) fail(RecordMutationError.OVERLAP)
        proposed.forEach {
            if (it.typeId != 0L && database.recordTypeDao().get(it.typeId) == null) fail(RecordMutationError.INVALID_TYPE)
            validateTags(it.tags)
        }
        validator.validate(proposed, allRecords().filter { it.id !in ignored }, active().filter { it.id !in ignoredRunning }, now)
    }

    private suspend fun validateRunning(record: RunningRecord, now: Long, ignored: Set<Long> = emptySet()) {
        if (record.id <= 0 || database.recordTypeDao().get(record.id) == null) fail(RecordMutationError.INVALID_TYPE)
        if (record.timeStarted > now) fail(RecordMutationError.FUTURE_TIME)
        if (allRecords().any { it.id !in ignored && it.timeEnded > it.timeStarted && it.timeEnded <= now && it.timeEnded > record.timeStarted }) {
            fail(RecordMutationError.OVERLAP)
        }
        validateTags(record.tags)
    }

    private suspend fun validateTags(tags: List<RecordBase.Tag>) {
        if (tags.map { it.tagId }.distinct().size != tags.size) fail(RecordMutationError.TAG_CONFLICT)
        val settings = database.lifeClockSettingsDao().get()
        val ratingIds = settings?.let { setOf(it.happinessTagId, it.meaningTagId) }.orEmpty()
        tags.filter { it.tagId in ratingIds }.forEach {
            val value = it.numericValue
            if (value != null && (value !in (if (it.tagId == settings?.meaningTagId) -5.0 else 1.0)..5.0 || value % 1.0 != 0.0)) fail(RecordMutationError.INVALID_SCORE)
        }
    }

    private fun mergeTags(previous: List<RecordBase.Tag>, current: List<RecordBase.Tag>): List<RecordBase.Tag> {
        val result = previous.associateBy { it.tagId }.toMutableMap()
        current.forEach {
            val old = result[it.tagId]
            if (old?.numericValue != null && it.numericValue != null && old.numericValue != it.numericValue) fail(RecordMutationError.TAG_CONFLICT)
            result[it.tagId] = if (it.numericValue == null && old?.numericValue != null) old else it
        }
        return result.values.toList()
    }

    private suspend fun write(record: Record): Long {
        val id = database.recordDao().insert(recordMapper.map(record))
        database.recordToRecordTagDao().deleteAllByRecordId(id)
        database.recordToRecordTagDao().insert(record.tags.map { tagMapper.map(id, it) })
        return id
    }

    private suspend fun writeRunning(record: RunningRecord) {
        database.runningRecordDao().insert(runningMapper.map(record))
        database.runningRecordToRecordTagDao().deleteAllByRecordId(record.id)
        database.runningRecordToRecordTagDao().insert(record.tags.map { runningTagMapper.map(record.id, it) })
    }

    private suspend fun writePhotos(ownerId: Long, runningOwner: Boolean, photos: List<RecordPhoto>) {
        val dao = database.recordPhotoDao()
        val existing = if (runningOwner) dao.getByRunningRecord(ownerId) else dao.getByRecord(ownerId)
        val byId = existing.associateBy { it.id }
        require(photos.filter { it.id != 0L }.map { it.id }.distinct().size == photos.count { it.id != 0L })
        photos.forEachIndexed { position, photo ->
            if (photo.id != 0L) {
                val old = requireNotNull(byId[photo.id]) { "Photo belongs to another record" }
                require(old.relativePath == photo.relativePath && old.sha256 == photo.sha256)
            }
            dao.upsert(
                RecordPhotoDBO(
                    id = photo.id, recordId = if (runningOwner) null else ownerId,
                    runningRecordId = if (runningOwner) ownerId else null,
                    relativePath = photo.relativePath, sha256 = photo.sha256, position = position,
                ),
            )
        }
        existing.filter { old -> photos.none { it.id == old.id } }.forEach { dao.delete(it.id) }
    }

    private suspend fun deleteRunning(id: Long) {
        database.runningRecordToRecordTagDao().deleteAllByRecordId(id)
        database.runningRecordDao().delete(id)
    }

    private fun ended(record: RunningRecord, end: Long): Record = Record(
        typeId = record.id, timeStarted = record.timeStarted, timeEnded = end, comment = record.comment,
        tags = record.tags, summary = record.summary, zoneId = record.zoneId, templateId = record.templateId,
        reviewState = validator.state(record.timeStarted, end),
    )

    private fun fail(reason: RecordMutationError): Nothing = throw RecordMutationException(reason)
}
