import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { ApiService } from '../core/api.service';
import { MonitoringPage } from './monitoring.page';

describe('MonitoringPage', () => {
  let fixture: ComponentFixture<MonitoringPage>;

  beforeEach(async () => {
    const api = {
      monitoring: () => of({
        prometheusUp: true,
        message: null,
        namespace: 'dcp',
        cpuCores: 0.2,
        memoryBytes: 1024,
        podCount: 16,
        restartCount: 0,
        controlPlaneHeapBytes: 1024,
        scrapedAt: '2026-10-11T21:00:00Z'
      }),
      grafana: () => of({ url: 'http://127.0.0.1:13001', dashboardPath: '/d/dcp-overview' }),
      series: () => of({ metric: 'cpu', range: '1h', timestamps: [], values: [], message: null })
    };
    await TestBed.configureTestingModule({
      imports: [MonitoringPage],
      providers: [{ provide: ApiService, useValue: api }]
    }).compileComponents();
    fixture = TestBed.createComponent(MonitoringPage);
    fixture.detectChanges();
  });

  it('embeds only the local Grafana dashboard', () => {
    const frame = fixture.nativeElement.querySelector('iframe') as HTMLIFrameElement | null;
    expect(frame?.src).toBe('http://127.0.0.1:13001/d/dcp-overview?orgId=1&kiosk');
    fixture.componentInstance.grafana.set({ url: 'https://grafana.example', dashboardPath: '/d/dcp-overview' });
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('iframe')).toBeNull();
  });
});
