/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** Overrides the backend origin. Defaults to http://localhost:8080. */
  readonly VITE_API_BASE?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
