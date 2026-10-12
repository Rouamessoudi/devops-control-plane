import { Chart, CategoryScale, LinearScale, LineController, LineElement, PointElement, Tooltip, type ChartDataset } from 'chart.js';
import { seriesLabels } from '../core/ui';

Chart.register(LineController, LineElement, PointElement, LinearScale, CategoryScale, Tooltip);

export function clearLine(canvas: HTMLCanvasElement): void {
  Chart.getChart(canvas)?.destroy();
}

export function renderLine(canvas: HTMLCanvasElement, label: string, timestamps: number[], values: number[], color: string): void {
  Chart.getChart(canvas)?.destroy();
  const dataset: ChartDataset<'line', number[]> = {
    label,
    data: values,
    borderColor: color,
    backgroundColor: color,
    tension: 0.25,
    pointRadius: 0,
    borderWidth: 2
  };
  const chart = new Chart(canvas, {
    type: 'line',
    data: { labels: seriesLabels(timestamps), datasets: [dataset] },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      plugins: { legend: { display: false } },
      scales: {
        x: { ticks: { maxTicksLimit: 6, color: '#93a0bd' }, grid: { display: false } },
        y: { ticks: { maxTicksLimit: 5, color: '#93a0bd' }, grid: { color: 'rgba(147,160,189,0.15)' } }
      }
    }
  });
  if (Chart.getChart(canvas) !== chart) {
    chart.destroy();
  }
}
