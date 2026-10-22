export interface Statistics {
  notes: number; books: number; activeDays: number; today: string; days: Record<string, number>;
}
export function calendarDays(year: number): (string | null)[] {
  const first = new Date(Date.UTC(year, 0, 1));
  const cells: (string | null)[] = Array.from({ length: first.getUTCDay() }, () => null);
  for (const day = new Date(first); day.getUTCFullYear() === year; day.setUTCDate(day.getUTCDate() + 1)) {
    cells.push(day.toISOString().slice(0, 10));
  }
  while (cells.length % 7) cells.push(null);
  return cells;
}
export function activityLevel(count: number): number {
  return count === 0 ? 0 : count <= 5 ? 1 : count <= 15 ? 2 : count <= 30 ? 3 : 4;
}
