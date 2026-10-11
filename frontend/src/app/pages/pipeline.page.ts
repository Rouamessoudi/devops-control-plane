import { Component, inject, signal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { MatButton } from '@angular/material/button';
import { ApiService } from '../core/api.service';
import { PipelineView } from '../core/models';
import { badgeClass, errorMessage } from '../core/ui';

@Component({
  selector: 'app-pipeline',
  imports: [MatButton],
  template: `
    <section class="page">
      <div class="page-head">
        <h1>Pipeline</h1>
        <button mat-stroked-button type="button" (click)="load()">Actualiser</button>
      </div>
      @if (error()) { <p class="error-banner">{{ error() }}</p> }
      @if (pipeline(); as item) {
        <p><span [class]="'badge ' + badge(item.status)">{{ item.status }}</span> {{ item.repositoryUrl }} · {{ item.branch }}</p>
        <p class="muted">Début {{ item.startedAt }} · Fin {{ item.finishedAt || 'en cours' }} · Conclusion {{ item.conclusion || '—' }}</p>
        <article class="panel table-wrap">
          <table class="data">
            <thead><tr><th>Étape</th><th>Statut</th><th>Journal</th></tr></thead>
            <tbody>
              @for (step of item.steps; track step.name) {
                <tr>
                  <td>{{ step.name }}</td>
                  <td><span [class]="'badge ' + badge(step.status)">{{ step.status }}</span></td>
                  <td>{{ step.log }}</td>
                </tr>
              }
            </tbody>
          </table>
        </article>
        <pre class="log">{{ logs() }}</pre>
      }
    </section>
  `
})
export class PipelinePage {
  private readonly api = inject(ApiService);
  private readonly id = inject(ActivatedRoute).snapshot.paramMap.get('id') ?? '';
  readonly pipeline = signal<PipelineView | null>(null);
  readonly logs = signal('');
  readonly error = signal('');
  readonly badge = badgeClass;

  constructor() { this.load(); }

  load(): void {
    this.api.pipeline(this.id).subscribe({ next: (item) => this.pipeline.set(item), error: (err: unknown) => this.error.set(errorMessage(err)) });
    this.api.pipelineLogs(this.id).subscribe({ next: (text) => this.logs.set(text), error: () => this.logs.set('') });
  }
}
