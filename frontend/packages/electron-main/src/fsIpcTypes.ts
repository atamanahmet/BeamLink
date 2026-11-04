export type FsEntryResult = {
  name: string;
  fullPath: string;
  isDirectory: boolean;
  size?: number;
  locked: boolean;
  hidden: boolean;
};

export type FsRootResult = {
  name: string;
  fullPath: string;
  isDirectory: true;
  locked: boolean;
  hidden: boolean;
};

export type FsOk<T> = { ok: true; data: T };
export type FsErr = { ok: false; code: string };
export type FsResult<T> = FsOk<T> | FsErr;
