import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatButton } from '@angular/material/button';
import { ApiService } from '../core/api.service';
import { PipelineView } from '../core/models';
import { badgeClass, errorMessage } from '../core/ui';

@Component({
  selector: 'app-pipelines',
  imports: [RouterLink, MatButton],
  template: `
    <section class="page">
      <div class="page-head">
        <div>
          <h1>Pipelines</h1>
          <p class="muted">Un pipeline se lance depuis la fiche d'une application. Cette page suit ceux déjà demandés.</p>
        </div>
        <button mat-stroked-button type="button" (click)="load()">Actualiser</button>
      </div>
      @if (error()) { <p class="error-banner">{{ error() }}</p> }
      @if (loading()) { <p class="muted">Chargement…</p> }
      <article class="panel table-wrap">
        <table class="data">
          <thead><tr><th>Statut</th><th>Dépôt</th><th>Branche</th><th>Début</th><th></th></tr></thead>
          <tbody>
            @for (item of items(); track item.id) {
              <tr>
                <td><span [class]="'badge ' + badge(item.status)">{{ item.status }}</span></td>
                <td>{{ item.repositoryUrl }}</td>
                <td>{{ item.branch }}</td>
                <td>{{ item.startedAt }}</td>
                <td><a mat-button [routerLink]="['/pipelines', item.id]">Ouvrir</a></td>
              </tr>
            } @empty { <tr><td colspan="5">Aucun pipeline.</td></tr> }
          </tbody>
        </table>
      </article>
    </section>
  `
})
export class PipelinesPage {
  private readonly api = inject(ApiService);
  readonly items = signal<PipelineView[]>([]);
  readonly error = signal('');
  readonly loading = signal(true);
  readonly badge = badgeClass;

  constructor() {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.api.pipelines().subscribe({
      next: (page) => {
        this.items.set(page.items);
        this.error.set('');
        this.loading.set(false);
      },
      error: (err: unknown) => {
        this.error.set(errorMessage(err));
        this.loading.set(false);
      }
    });
  }
}
