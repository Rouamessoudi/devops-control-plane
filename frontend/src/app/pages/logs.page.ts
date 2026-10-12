import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButton } from '@angular/material/button';
import { MatFormField, MatLabel } from '@angular/material/form-field';
import { MatInput } from '@angular/material/input';
import { ApiService } from '../core/api.service';
import { errorMessage } from '../core/ui';

@Component({
  selector: 'app-logs',
  imports: [FormsModule, MatButton, MatFormField, MatLabel, MatInput],
  template: `
    <section class="page">
      <h1>Journaux</h1>
      @if (error()) { <p class="error-banner">{{ error() }}</p> }
      <div class="row-actions">
        <mat-form-field appearance="outline"><mat-label>Nom du pod</mat-label><input matInput [(ngModel)]="pod"></mat-form-field>
        <button mat-flat-button type="button" (click)="load()">Lire les logs Kubernetes</button>
      </div>
      <pre class="log">{{ text() || 'Aucun journal chargé.' }}</pre>
    </section>
  `
})
export class LogsPage {
  private readonly api = inject(ApiService);
  pod = '';
  readonly text = signal('');
  readonly error = signal('');

  load(): void {
    if (!this.pod.trim()) {
      this.error.set('Indiquez le nom du Pod.');
      return;
    }
    this.api.monitorLogs(this.pod.trim()).subscribe({
      next: (text) => this.text.set(text),
      error: (err: unknown) => this.error.set(errorMessage(err))
    });
  }
}
