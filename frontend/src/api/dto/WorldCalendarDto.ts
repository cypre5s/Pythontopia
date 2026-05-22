export type WorldSeason = 'spring' | 'summer' | 'autumn' | 'winter';

export interface WorldCalendarDto {
  dayIndex: number;
  weekday: string;
  monthIndex: number;
  season: WorldSeason;
  timeOfDay: string;
  weather: string;
}
