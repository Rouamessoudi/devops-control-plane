import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { AuthService } from './auth.service';
import { authInterceptor } from './auth.interceptor';
import { TokenStore } from './token-store';

describe('AuthService', () => {
  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(withInterceptors([authInterceptor])), provideHttpClientTesting()]
    });
  });

  it('stores the session returned by login', () => {
    const service = TestBed.inject(AuthService);
    const http = TestBed.inject(HttpTestingController);
    service.login('dev@dcp.local', 'ChangeMe!2026').subscribe();
    const request = http.expectOne('/api/auth/login');
    expect(request.request.body).toEqual({ email: 'dev@dcp.local', password: 'ChangeMe!2026' });
    request.flush({
      accessToken: 'access',
      refreshToken: 'refresh',
      expiresIn: 900,
      tokenType: 'Bearer',
      user: { id: '1', email: 'dev@dcp.local', fullName: 'Dev', roles: ['DEVELOPER'], createdAt: '2026-01-01T00:00:00Z' }
    });
    expect(TestBed.inject(TokenStore).accessToken()).toBe('access');
    http.verify();
  });
});
