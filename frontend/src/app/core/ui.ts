import { HttpErrorResponse } from '@angular/common/http';

export function errorMessage(error: unknown): string {
  if (error instanceof HttpErrorResponse) {
    const body = error.error as { detail?: string; message?: string } | string | null;
    if (typeof body === 'string' && body.trim()) {
      return body;
    }
    if (body && typeof body === 'object' && (body.detail || body.message)) {
      return body.detail || body.message || error.message;
    }
    return error.message;
  }
  return 'La requête a échoué.';
}

export function badgeClass(status: string | null | undefined): string {
  const value = (status ?? '').toUpperCase().replace(/[^A-Z0-9]/g, '');
  if (['SUCCEEDED', 'RUNNING', 'READY', 'ACTIVE', 'UP', 'BOUND', 'HEALTHY'].includes(value)) {
    return 'ok';
  }
  if (['FAILED', 'ERROR', 'DOWN', 'CRASHLOOPBACKOFF', 'EXITED'].includes(value)) {
    return 'bad';
  }
  if (['PENDING', 'DEPLOYING', 'CREATED', 'SKIPPED', 'CONTAINERCREATING', 'WAITING'].includes(value)) {
    return 'warn';
  }
  return '';
}

export function formatBytes(value: number | null | undefined): string {
  if (value == null || Number.isNaN(value)) {
    return '—';
  }
  const units = ['o', 'Kio', 'Mio', 'Gio'];
  let size = value;
  let unit = 0;
  while (size >= 1024 && unit < units.length - 1) {
    size /= 1024;
    unit += 1;
  }
  return `${size >= 10 || unit === 0 ? size.toFixed(0) : size.toFixed(1)} ${units[unit]}`;
}

export function formatAge(value: string | null | undefined, now = Date.now()): string {
  if (!value) {
    return '—';
  }
  const time = Date.parse(value);
  if (Number.isNaN(time)) {
    return value;
  }
  const seconds = Math.max(0, Math.floor((now - time) / 1000));
  if (seconds < 60) {
    return `${seconds} s`;
  }
  const minutes = Math.floor(seconds / 60);
  if (minutes < 60) {
    return `${minutes} min`;
  }
  const hours = Math.floor(minutes / 60);
  if (hours < 48) {
    return `${hours} h`;
  }
  return `${Math.floor(hours / 24)} j`;
}

export function formatMetric(value: number | null | undefined): string {
  if (value == null || Number.isNaN(value)) {
    return '—';
  }
  if (Number.isInteger(value)) {
    return String(value);
  }
  const abs = Math.abs(value);
  if (abs >= 100) {
    return value.toFixed(0);
  }
  if (abs >= 1) {
    return value.toFixed(2);
  }
  return value.toPrecision(3);
}

export function seriesLabels(timestamps: number[]): string[] {
  return timestamps.map((stamp) => {
    const millis = stamp < 10_000_000_000 ? stamp * 1000 : stamp;
    return new Date(millis).toLocaleTimeString('fr-FR', { hour: '2-digit', minute: '2-digit' });
  });
}
