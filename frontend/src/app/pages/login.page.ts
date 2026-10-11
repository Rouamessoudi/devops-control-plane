import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { MatButton } from '@angular/material/button';
import { MatError, MatFormField, MatLabel } from '@angular/material/form-field';
import { MatInput } from '@angular/material/input';
import { AuthService } from '../core/auth.service';
import { errorMessage } from '../core/ui';

@Component({
  selector: 'app-login',
  imports: [ReactiveFormsModule, RouterLink, MatFormField, MatLabel, MatError, MatInput, MatButton],
  template: `
    <section class="auth-screen">
      <form class="auth-card" [formGroup]="form" (ngSubmit)="submit()">
        <h1>DevOps Control Plane</h1>
        <p class="muted">Connexion au plan de contrôle.</p>
        @if (error()) { <p class="error-banner">{{ error() }}</p> }
        <mat-form-field appearance="outline">
          <mat-label>Email</mat-label>
          <input matInput type="email" formControlName="email" autocomplete="username">
          @if (form.controls.email.invalid && form.controls.email.touched) { <mat-error>Email requis</mat-error> }
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Mot de passe</mat-label>
          <input matInput type="password" formControlName="password" autocomplete="current-password">
        </mat-form-field>
        <button mat-flat-button type="submit" [disabled]="form.invalid || busy()">Se connecter</button>
        <p>Pas de compte ? <a routerLink="/register">Créer un compte</a></p>
      </form>
    </section>
  `,
  styles: [`mat-form-field { width: 100%; display: block; } button { width: 100%; }`]
})
export class LoginPage {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  readonly form = inject(FormBuilder).nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', Validators.required]
  });
  readonly error = signal('');
  readonly busy = signal(false);

  submit(): void {
    if (this.form.invalid) {
      return;
    }
    this.busy.set(true);
    this.error.set('');
    this.auth.login(this.form.controls.email.value, this.form.controls.password.value).subscribe({
      next: () => void this.router.navigate(['/dashboard']),
      error: (err: unknown) => {
        this.error.set(errorMessage(err));
        this.busy.set(false);
      }
    });
  }
}
