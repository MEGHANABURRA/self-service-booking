export class AppError extends Error {
  constructor(
    public statusCode: number,
    public code: string,
    message: string,
  ) {
    super(message);
  }
}

export const notFound = (msg: string) => new AppError(404, 'NOT_FOUND', msg);
export const conflict = (msg: string) => new AppError(409, 'CONFLICT', msg);
export const forbidden = (msg: string) => new AppError(403, 'FORBIDDEN', msg);
export const badRequest = (msg: string) => new AppError(400, 'BAD_REQUEST', msg);
