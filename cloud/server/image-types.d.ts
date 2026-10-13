declare module 'bmp-js' {
  const bmp: { decode(buffer: Buffer): { width: number; height: number; data: Buffer }; encode(image: { width: number; height: number; data: Buffer }): { data: Buffer } };
  export default bmp;
}
declare module 'heic-decode' {
  const decode: { all(options: { buffer: Buffer }): Promise<Array<{ width: number; height: number; decode(): Promise<{ width: number; height: number; data: Uint8ClampedArray }> }> & { dispose(): void }> };
  export default decode;
}
