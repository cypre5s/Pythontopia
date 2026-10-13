declare module 'pyodide/pyodide.asm.mjs' {
  import type { PyodideConfig } from 'pyodide';

  const createPyodideModule: NonNullable<PyodideConfig['createPyodideModule']>;
  export default createPyodideModule;
}
