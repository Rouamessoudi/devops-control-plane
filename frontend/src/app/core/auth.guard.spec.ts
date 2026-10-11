import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { authGuard, adminGuard } from './auth.guard';
import { TokenStore } from './token-store';

describe('authGuard', () => {
  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({ providers: [provideRouter([])] });
  });

  it('sends an anonymous user to login', () => {
    const result = TestBed.runInInjectionContext(() => authGuard({} as never, {} as never));
    expect(TestBed.inject(Router).serializeUrl(result as never)).toBe('/login');
  });

  it('allows a user who has an access token', () => {
    TestBed.inject(TokenStore).setSession('access', 'refresh', { email: 'a@b.c', fullName: 'A', roles: ['DEVELOPER'] });
    const result = TestBed.runInInjectionContext(() => authGuard({} as never, {} as never));
    expect(result).toBe(true);
  });

  it('blocks a developer from the admin area', () => {
    TestBed.inject(TokenStore).setSession('access', 'refresh', { email: 'a@b.c', fullName: 'A', roles: ['DEVELOPER'] });
    const result = TestBed.runInInjectionContext(() => adminGuard({} as never, {} as never));
    expect(TestBed.inject(Router).serializeUrl(result as never)).toBe('/dashboard');
  });
});
