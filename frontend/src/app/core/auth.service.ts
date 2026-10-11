import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, tap } from 'rxjs';
import { AuthResponse, UserProfile } from './models';
import { TokenStore } from './token-store';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly tokens = inject(TokenStore);

  register(body: { email: string; password: string; fullName: string }): Observable<UserProfile> {
    return this.http.post<UserProfile>('/api/auth/register', body);
  }

  login(email: string, password: string): Observable<AuthResponse> {
    return this.http.post<AuthResponse>('/api/auth/login', { email, password }).pipe(
      tap((response) => this.tokens.setSession(response.accessToken, response.refreshToken, response.user))
    );
  }

  refresh(): Observable<AuthResponse> {
    return this.http.post<AuthResponse>('/api/auth/refresh', { refreshToken: this.tokens.refreshToken() }).pipe(
      tap((response) => this.tokens.setSession(response.accessToken, response.refreshToken, response.user))
    );
  }

  me(): Observable<UserProfile> {
    return this.http.get<UserProfile>('/api/auth/me').pipe(
      tap((user) => {
        const refresh = this.tokens.refreshToken();
        const access = this.tokens.accessToken();
        if (access && refresh) {
          this.tokens.setSession(access, refresh, user);
        }
      })
    );
  }

  updateProfile(fullName: string): Observable<UserProfile> {
    return this.http.put<UserProfile>('/api/auth/me', { fullName });
  }

  changePassword(currentPassword: string, newPassword: string): Observable<void> {
    return this.http.put<void>('/api/auth/password', { currentPassword, newPassword });
  }

  logout(): void {
    this.tokens.clear();
  }
}
