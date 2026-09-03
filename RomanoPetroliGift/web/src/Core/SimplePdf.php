<?php
// Copyright (c) Roberto Di Flumeri

namespace App\Core;

/**
 * Generatore PDF minimale, senza dipendenze esterne (niente Composer/librerie di terze parti).
 * Supporta un'unica pagina con testo (font standard Helvetica/Helvetica-Bold, nessun embedding
 * necessario) e rettangoli/linee vettoriali. Sufficiente per documenti semplici come un voucher.
 */
class SimplePdf
{
    private float $width;
    private float $height;
    private array $ops = [];

    public function __construct(float $width = 595.28, float $height = 841.89)
    {
        $this->width = $width;
        $this->height = $height;
    }

    public function text(float $x, float $y, string $text, string $font = 'F1', float $size = 12): void
    {
        $encoded = @mb_convert_encoding($text, 'Windows-1252', 'UTF-8') ?: $text;
        $escaped = str_replace(['\\', '(', ')'], ['\\\\', '\\(', '\\)'], $encoded);
        $this->ops[] = sprintf('BT /%s %.2F Tf %.2F %.2F Td (%s) Tj ET', $font, $size, $x, $y, $escaped);
    }

    public function rect(float $x, float $y, float $w, float $h, bool $stroke = true, bool $fill = false): void
    {
        $mode = $fill && $stroke ? 'B' : ($fill ? 'f' : 'S');
        $this->ops[] = sprintf('%.2F %.2F %.2F %.2F re %s', $x, $y, $w, $h, $mode);
    }

    public function line(float $x1, float $y1, float $x2, float $y2, float $width = 1): void
    {
        $this->ops[] = sprintf('%.2F w %.2F %.2F m %.2F %.2F l S', $width, $x1, $y1, $x2, $y2);
    }

    public function setColor(float $r, float $g, float $b): void
    {
        $this->ops[] = sprintf('%.3F %.3F %.3F rg %.3F %.3F %.3F RG', $r, $g, $b, $r, $g, $b);
    }

    public function output(): string
    {
        $content = implode("\n", $this->ops);
        $objects = [];

        $objects[] = "<< /Type /Catalog /Pages 2 0 R >>";
        $objects[] = "<< /Type /Pages /Kids [3 0 R] /Count 1 >>";
        $objects[] = sprintf(
            "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 %.2F %.2F] /Resources << /Font << /F1 5 0 R /F2 6 0 R >> >> /Contents 4 0 R >>",
            $this->width,
            $this->height
        );
        $objects[] = sprintf("<< /Length %d >>\nstream\n%s\nendstream", strlen($content), $content);
        $objects[] = "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>";
        $objects[] = "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold /Encoding /WinAnsiEncoding >>";

        $pdf = "%PDF-1.4\n";
        $offsets = [];
        foreach ($objects as $i => $body) {
            $offsets[] = strlen($pdf);
            $pdf .= ($i + 1) . " 0 obj\n" . $body . "\nendobj\n";
        }

        $xrefStart = strlen($pdf);
        $count = count($objects) + 1;
        $pdf .= "xref\n0 {$count}\n0000000000 65535 f \n";
        foreach ($offsets as $offset) {
            $pdf .= sprintf("%010d 00000 n \n", $offset);
        }
        $pdf .= "trailer\n<< /Size {$count} /Root 1 0 R >>\nstartxref\n{$xrefStart}\n%%EOF";

        return $pdf;
    }
}
