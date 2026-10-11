import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { MatButton } from '@angular/material/button';
import { MatError, MatFormField, MatLabel } from '@angular/material/form-field';
import { MatInput } from '@angular/material/input';
import { AuthService } from '../core/auth.service';
import { errorMessage } from '../core/ui';

@Component({
  selector: 'app-register',
  imports: [ReactiveFormsModule, RouterLink, MatFormField, MatLabel, MatError, MatInput, MatButton],
  template: `
    <section class="auth-screen">
      <form class="auth-card" [formGroup]="form" (ngSubmit)="submit()">
        <h1>Créer un compte</h1>
        <p class="muted">Le rôle attribué est DEVELOPER.</p>
        @if (error()) { <p class="error-banner">{{ error() }}</p> }
        <mat-form-field appearance="outline">
          <mat-label>Nom</mat-label>
          <input matInput formControlName="fullName">
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Email</mat-label>
          <input matInput type="email" formControlName="email">
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Mot de passe</mat-label>
          <input matInput type="password" formControlName="password">
          @if (form.controls.password.invalid && form.controls.password.touched) {
            <mat-error>10 caractères minimum, avec majuscule, minuscule et chiffre</mat-error>
          }
        </mat-form-field>
        <button mat-flat-button type="submit" [disabled]="form.invalid || busy()">Créer le compte</button>
        <p><a routerLink="/login">Retour à la connexion</a></p>
      </form>
    </section>
  `,
  styles: [`mat-form-field { width: 100%; display: block; } button { width: 100%; }`]
})
export class RegisterPage {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  readonly form = inject(FormBuilder).nonNullable.group({
    fullName: ['', Validators.required],
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required, Validators.minLength(10), Validators.pattern(/^(?=.*[a-z])(?=.*[A-Z])(?=.*\d).+$/)]]
  });
  readonly error = signal('');
  readonly busy = signal(false);

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.busy.set(true);
    const value = this.form.getRawValue();
    this.auth.register(value).subscribe({
      next: () => this.auth.login(value.email, value.password).subscribe({
        next: () => void this.router.navigate(['/dashboard']),
        error: (err: unknown) => {
          this.error.set(errorMessage(err));
          this.busy.set(false);
        }
      }),
      error: (err: unknown) => {
        this.error.set(errorMessage(err));
        this.busy.set(false);
      }
    });
  }
}
