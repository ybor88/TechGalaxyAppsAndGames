<?php

// Mostra i punti senza decimali inutili (120 punti) ma con 2 decimali quando frazionari (4,50 punti).
function format_punti(float $punti): string
{
    if (fmod($punti, 1.0) === 0.0) {
        return number_format($punti, 0, ',', '.');
    }

    return number_format($punti, 2, ',', '.');
}
