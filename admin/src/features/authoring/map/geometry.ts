import type { Interaction, Point } from "@/features/authoring/model";
import { type MouseEvent, type PointerEvent } from "react";
export type Region = Extract<
  Interaction["target"],
  { region: unknown }
>["region"];
export function markerAnchor(element: SVGElement): Point {
  const bounds = element.getBoundingClientRect();
  return {
    x: bounds.left + bounds.width / 2,
    y: bounds.top + bounds.height / 2,
  };
}
export function eventAnchor(event: MouseEvent | PointerEvent): Point {
  return { x: event.clientX, y: event.clientY };
}
export function regionBetween(start: Point, end: Point): Region {
  return {
    x: Math.min(start.x, end.x),
    y: Math.min(start.y, end.y),
    width: Math.abs(start.x - end.x) + 1,
    height: Math.abs(start.y - end.y) + 1,
  };
}
