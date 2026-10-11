import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { TokenStore } from './token-store';

export const authGuard: CanActivateFn = () => {
  const tokens = inject(TokenStore);
  const router = inject(Router);
  if (tokens.accessToken()) {
    return true;
  }
  return router.createUrlTree(['/login']);
};

export const guestGuard: CanActivateFn = () => {
  const tokens = inject(TokenStore);
  const router = inject(Router);
  if (!tokens.accessToken()) {
    return true;
  }
  return router.createUrlTree(['/dashboard']);
};

export const adminGuard: CanActivateFn = () => {
  const tokens = inject(TokenStore);
  const router = inject(Router);
  if (tokens.accessToken() && tokens.hasRole('ADMIN')) {
    return true;
  }
  return router.createUrlTree(['/dashboard']);
};
