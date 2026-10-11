import { HttpErrorResponse } from '@angular/common/http';
import { badgeClass, errorMessage } from './ui';

describe('errorMessage', () => {
  it('reads the API detail', () => {
    const error = new HttpErrorResponse({ error: { detail: 'Invalid token' }, status: 401, statusText: 'Unauthorized' });
    expect(errorMessage(error)).toBe('Invalid token');
  });

  it('keeps a plain error body', () => {
    const error = new HttpErrorResponse({ error: 'gateway down', status: 502, statusText: 'Bad Gateway' });
    expect(errorMessage(error)).toBe('gateway down');
  });

  it('does not invent a message for an unknown failure', () => {
    expect(errorMessage(new Error('socket'))).toBe('La requête a échoué.');
  });
});

describe('badgeClass', () => {
  it('classifies known deployment states', () => {
    expect(badgeClass('SUCCEEDED')).toBe('ok');
    expect(badgeClass('FAILED')).toBe('bad');
    expect(badgeClass('PENDING')).toBe('warn');
    expect(badgeClass(null)).toBe('');
  });
});
