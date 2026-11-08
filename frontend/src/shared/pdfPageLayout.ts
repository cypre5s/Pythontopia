/** Fit the entire original page inside its available reading surface. */
export function fitPdfPage(
  pageWidth: number,
  pageHeight: number,
  availableWidth: number,
  availableHeight: number,
  pixelRatio = 1,
): { width: number; height: number; renderScale: number } | undefined {
  if (![pageWidth, pageHeight, availableWidth, availableHeight].every(value => Number.isFinite(value) && value > 0))
    return undefined;
  const scale = Math.min(availableWidth / pageWidth, availableHeight / pageHeight),
    resolution = Number.isFinite(pixelRatio) && pixelRatio > 0 ? pixelRatio : 1;
  return { width: pageWidth * scale, height: pageHeight * scale, renderScale: scale * resolution };
}
