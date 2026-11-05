package ru.kazna;

import java.util.ArrayList;
import java.util.List;

/** Всё состояние государства. Сохраняется в config/kazna.json */
public class GovState {
    public String rulerUuid = null;
    public String rulerName = null;
    public int treasury = 0;          // казна в золотых слитках
    public int taxPercent = 10;       // налог с золота в инвентаре, %
    public int intervalMinutes = 10;  // как часто собирается налог
    public int artemDecreeMinutes = 7; // как часто Артём III издаёт указы
    public List<String> laws = new ArrayList<>();
}
