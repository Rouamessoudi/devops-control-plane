import { HttpErrorResponse } from '@angular/common/http';

export function errorMessage(error: unknown): string {
  if (error instanceof HttpErrorResponse) {
    const body = error.error as { detail?: string; message?: string } | string | null;
    if (typeof body === 'string' && body.trim()) {
      return body;
    }
    if (body && typeof body === 'object' && (body.detail || body.message)) {
      return body.detail || body.message || error.message;
    }
    return error.message;
  }
  return 'La requête a échoué.';
}

export function badgeClass(status: string | null | undefined): string {
  const value = (status ?? '').toUpperCase();
  if (['SUCCEEDED', 'RUNNING', 'READY', 'ACTIVE', 'UP'].includes(value)) {
    return 'ok';
  }
  if (['FAILED', 'ERROR', 'DOWN'].includes(value)) {
    return 'bad';
  }
  if (['PENDING', 'DEPLOYING', 'CREATED', 'SKIPPED'].includes(value)) {
    return 'warn';
  }
  return '';
}
