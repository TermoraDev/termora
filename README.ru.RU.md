<div align="center">
<a href="./README.md">English</a> | <a href="./README.de_DE.md">Deutsch</a> | <a href="./README.zh_CN.md">简体中文</a> | <a href="./README.pt_BR.md">Português (Brasil)</a>
</div>

# Termora

**Termora** — кроссплатформенный эмулятор терминала и SSH-клиент для **Windows, macOS и Linux**.

<div align="center">
  <img src="docs/readme.png" alt="Интерфейс Termora" />
</div>

Проект Termora разработан на [**Kotlin/JVM**](https://kotlinlang.org/) и частично поддерживает [**управляющие последовательности XTerm**](https://invisible-island.net/xterm/ctlseqs/ctlseqs.html). Долгосрочная цель проекта — обеспечить **поддержку всех платформ**, включая Android, iOS и iPadOS, с помощью [**Kotlin Multiplatform**](https://kotlinlang.org/docs/multiplatform.html).



## ✨ Возможности

- 🧬 Кроссплатформенная работа
- 🔐 Встроенный менеджер ключей
- 🖼️ Перенаправление X11
- 🧑‍💻 Интеграция с SSH-агентом
- 💻 Отображение сведений о системе
- 📁 Управление файлами по SFTP через графический интерфейс
- 📊 Мониторинг загрузки видеокарт NVIDIA
- ⚡ Быстрый запуск команд


## 🚀 Передача файлов

- Прямая передача файлов между сервером A и сервером B
- Рекурсивная передача папок
- До **6 одновременных задач передачи**

<div align="center">
  <img src="docs/transfer.png" alt="Передача файлов" />
</div>



## 📝 Редактирование файлов

- Автоматическая отправка файла на сервер после редактирования и сохранения
- Переименование файлов и папок
- Быстрое удаление больших папок (поддерживается `rm -rf`)
- Наглядное изменение прав доступа
- Создание файлов и папок

<div align="center">
  <img src="docs/transfer-edit.png" alt="Редактирование файлов" />
</div>

## 💻 Хосты

- Древовидная иерархия, аналогичная структуре папок
- Назначение тегов отдельным хостам
- Импорт хостов из других программ
- Открытие хоста в инструменте передачи файлов

<div align="center">
  <img src="docs/host.png" alt="Управление хостами" />
</div>

## 🧩 Плагины

- 🌍 Geo: отображение географического расположения хостов
- 🔄 Sync: синхронизация настроек через Gist или WebDAV
- 🗂️ WebDAV: подключение к хранилищам WebDAV
- 📝 Editor: встроенный редактор файлов по SFTP
- 📡 SMB: подключение к ресурсам [SMB](https://ru.wikipedia.org/wiki/Server_Message_Block)
- ☁️ S3: подключение к объектным хранилищам S3
- ☁️ Huawei OBS: подключение к Huawei Cloud OBS
- ☁️ Tencent COS: подключение к Tencent Cloud COS
- ☁️ Alibaba OSS: подключение к Alibaba Cloud OSS
- 👉 [Посмотреть все плагины…](https://www.termora.app/plugins)




## 📦 Загрузка

- 🧾 [Последняя версия](https://github.com/TermoraDev/termora/releases/latest)
- 🍺 **Homebrew**: `brew install --cask termora`
- 🔨 **WinGet**: `winget install termora`
- <img src="https://apps.microsoft.com/assets/icons/logo-16x16.png" alt="Логотип Microsoft"/> <b>Microsoft Store</b>: <a href="https://apps.microsoft.com/store/detail/9NRZBHG43SB9?cid=DevShareMCLPCS">Открыть Termora в Microsoft Store</a>



## 🛠️ Разработка

Для разработки рекомендуется использовать JDK [JetBrains Runtime](https://github.com/JetBrains/JetBrainsRuntime).

- Локальный запуск: `./gradlew :run`


## 📄 Лицензия

Программное обеспечение распространяется по модели двойного лицензирования. Вы можете выбрать один из следующих вариантов:

- **AGPL-3.0**: использование, распространение и изменение программы на условиях лицензии [AGPL-3.0](https://opensource.org/license/agpl-v3).
- **Проприетарная лицензия**: для использования в закрытых или проприетарных проектах свяжитесь с автором, чтобы получить коммерческую лицензию.
