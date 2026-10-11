import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButton } from '@angular/material/button';
import { MatFormField, MatLabel } from '@angular/material/form-field';
import { MatInput } from '@angular/material/input';
import { AuthService } from '../core/auth.service';
import { TokenStore } from '../core/token-store';
import { errorMessage } from '../core/ui';

@Component({
  selector: 'app-settings',
  imports: [ReactiveFormsModule, MatFormField, MatLabel, MatInput, MatButton],
  template: `
    <section class="page">
      <h1>Paramètres</h1>
      <p class="muted">{{ tokens.user()?.email }} · {{ tokens.user()?.roles?.join(', ') }}</p>
      @if (message()) { <p>{{ message() }}</p> }
      @if (error()) { <p class="error-banner">{{ error() }}</p> }
      <form class="form-grid" [formGroup]="profile" (ngSubmit)="saveProfile()">
        <h2>Profil</h2>
        <mat-form-field appearance="outline"><mat-label>Nom</mat-label><input matInput formControlName="fullName"></mat-form-field>
        <button mat-flat-button type="submit">Enregistrer</button>
      </form>
      <form class="form-grid" [formGroup]="password" (ngSubmit)="savePassword()">
        <h2>Mot de passe</h2>
        <mat-form-field appearance="outline"><mat-label>Mot de passe actuel</mat-label><input matInput type="password" formControlName="currentPassword"></mat-form-field>
        <mat-form-field appearance="outline"><mat-label>Nouveau mot de passe</mat-label><input matInput type="password" formControlName="newPassword"></mat-form-field>
        <button mat-stroked-button type="submit">Changer</button>
      </form>
    </section>
  `
})
export class SettingsPage {
  private readonly auth = inject(AuthService);
  readonly tokens = inject(TokenStore);
  readonly message = signal('');
  readonly error = signal('');
  readonly profile = inject(FormBuilder).nonNullable.group({
    fullName: [this.tokens.user()?.fullName ?? '', Validators.required]
  });
  readonly password = inject(FormBuilder).nonNullable.group({
    currentPassword: ['', Validators.required],
    newPassword: ['', [Validators.required, Validators.minLength(10)]]
  });

  saveProfile(): void {
    this.auth.updateProfile(this.profile.controls.fullName.value).subscribe({
      next: () => this.message.set('Profil mis à jour.'),
      error: (err: unknown) => this.error.set(errorMessage(err))
    });
  }

  savePassword(): void {
    const value = this.password.getRawValue();
    this.auth.changePassword(value.currentPassword, value.newPassword).subscribe({
      next: () => this.message.set('Mot de passe modifié. Les sessions de rafraîchissement ont été révoquées.'),
      error: (err: unknown) => this.error.set(errorMessage(err))
    });
  }
}
