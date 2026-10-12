# ВМуткоин Обменник — Fabric 26.2

## Сборка Windows

Требуется **JDK 25**. Fabric 26.2 использует Java 25 и необфусцированные Mojang mappings.

Самый простой вариант:

```bat
build.bat
```

Или:

```bat
gradlew.bat build
```

Первый запуск автоматически скачает Gradle 9.7.0 из официального Gradle distribution service. Loom самостоятельно подключает репозитории Minecraft, Fabric и локальные артефакты.

Готовый мод находится в:

```text
build/libs/
```

## IntelliJ IDEA

Открой корневую папку проекта как Gradle project и дождись импорта.

## Важно

Minecraft 26.2 использует `net.fabricmc.fabric-loom`, официальные Mojang mappings и Java 25. Fabric API для 26.2 задаётся как обычная `implementation`-зависимость. В `settings.gradle` намеренно не включён режим `PREFER_SETTINGS`: он игнорирует локальные репозитории, которые добавляет Loom, и ломает разрешение Minecraft-артефактов.


## Правила выбора материалов

При автоматическом выборе недели первые три слота берутся только из `standardMaterialIds` — готовые материалы, ресурсы и сырые рудные материалы верхнего мира. Четвёртый слот всегда берётся из `specialMaterialIds` — эндер-жемчуг или каменные руды верхнего мира, рассчитанные на более ручную добычу. Кварц, кварцевая руда и остальные Nether-ресурсы не используются; изумрудная руда также специально исключена.

`currentMaterials` нормализуется под это правило при загрузке старой конфигурации, поэтому старый кварц не вернётся в выборку. Это не сбрасывает недельный прогресс игрока.

Недельный сброс автоматический: сервер раз в секунду проверяет календарный `weekKey` по настройкам `reset.day`, `reset.hour` и `reset.minute`. При переходе через заданную границу очищается прогресс всех игроков, при включённом `randomizeMaterials` выбираются новые 4 материала, а игрокам с открытым обменником сразу отправляется новый снимок состояния. Если сервер был выключен в момент сброса, сброс произойдёт при следующем запуске, если текущая календарная неделя уже изменилась.

## UI and purchase sound

The exchange and player inventory now use one shared outer Minecraft-style frame. The inventory starts immediately below the exchange area, with no 7 px overlap, so the first inventory row is not clipped. The frame keeps a light top/left bevel and darker right/bottom shadow.

Successful purchases produce a client-side `VILLAGER_TRADE` sound when the server snapshot reports that the weekly coin count increased. Opening the screen or a weekly reset does not trigger the sound.
