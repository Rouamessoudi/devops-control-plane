import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, switchMap, throwError } from 'rxjs';
import { AuthService } from './auth.service';
import { TokenStore } from './token-store';

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const tokens = inject(TokenStore);
  const auth = inject(AuthService);
  const router = inject(Router);
  const access = tokens.accessToken();
  const authed = access ? req.clone({ setHeaders: { Authorization: `Bearer ${access}` } }) : req;
  return next(authed).pipe(
    catchError((error: HttpErrorResponse) => {
      const isAuthCall = req.url.includes('/api/auth/login') || req.url.includes('/api/auth/refresh') || req.url.includes('/api/auth/register');
      if (error.status !== 401 || isAuthCall || req.headers.has('X-Retry') || !tokens.refreshToken()) {
        return throwError(() => error);
      }
      return auth.refresh().pipe(
        switchMap((response) => next(req.clone({
          setHeaders: { Authorization: `Bearer ${response.accessToken}`, 'X-Retry': '1' }
        }))),
        catchError((refreshError) => {
          auth.logout();
          void router.navigate(['/login']);
          return throwError(() => refreshError);
        })
      );
    })
  );
};
