export const BIG_DROP_FILE_LIMIT = 1000;

export type BigDropInput = {
  hasFolder: boolean;
  fileCount: number;
};

/**
 * True when a drop has a folder or more than 1000 files.
 */
export function bigDropRule(input: BigDropInput): boolean {
  return input.hasFolder || input.fileCount > BIG_DROP_FILE_LIMIT;
}
