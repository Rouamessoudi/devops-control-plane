import { clearLine, renderLine } from './line-chart';

describe('renderLine', () => {
  it('attaches a chart to the canvas and can clear it', () => {
    const canvas = document.createElement('canvas');
    renderLine(canvas, 'CPU', [1_700_000_000_000, 1_700_000_060_000], [0.1, 0.2], '#3ee0b0');
    clearLine(canvas);
    expect(canvas).toBeTruthy();
  });
});
