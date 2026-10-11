import { Injectable, signal } from '@angular/core';

const ACCESS = 'dcp_access';
const REFRESH = 'dcp_refresh';
const USER = 'dcp_user';

@Injectable({ providedIn: 'root' })
export class TokenStore {
  readonly accessToken = signal<string | null>(localStorage.getItem(ACCESS));
  readonly user = signal<{ email: string; fullName: string; roles: string[] } | null>(readUser());

  setSession(access: string, refresh: string, user: { email: string; fullName: string; roles: string[] }): void {
    localStorage.setItem(ACCESS, access);
    localStorage.setItem(REFRESH, refresh);
    localStorage.setItem(USER, JSON.stringify(user));
    this.accessToken.set(access);
    this.user.set(user);
  }

  setAccess(access: string): void {
    localStorage.setItem(ACCESS, access);
    this.accessToken.set(access);
  }

  refreshToken(): string | null {
    return localStorage.getItem(REFRESH);
  }

  clear(): void {
    localStorage.removeItem(ACCESS);
    localStorage.removeItem(REFRESH);
    localStorage.removeItem(USER);
    this.accessToken.set(null);
    this.user.set(null);
  }

  hasRole(role: string): boolean {
    return this.user()?.roles.includes(role) ?? false;
  }
}

function readUser(): { email: string; fullName: string; roles: string[] } | null {
  const raw = localStorage.getItem(USER);
  if (!raw) {
    return null;
  }
  try {
    return JSON.parse(raw) as { email: string; fullName: string; roles: string[] };
  } catch {
    return null;
  }
}
