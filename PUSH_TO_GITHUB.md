# Как выложить проект на GitHub

Репозиторий уже инициализирован локально (папка `konny-krug`, ветка `main`, один коммит).

## Вариант 1. Через сайт GitHub (без терминала)

1. github.com → **New repository** → имя `konny-krug` → **Private** или Public → **Create repository**.
2. На странице пустого репозитория: **uploading an existing file** → перетащите всё содержимое папки `konny-krug`
   (кроме `.gradle/` и `app/build/` — их можно не загружать, это кэш).
3. Commit changes.

## Вариант 2. Через терминал (аккуратнее)

```bash
cd konny-krug

# 1) создайте пустой репозиторий на GitHub, затем:
git remote add origin https://github.com/ВАШ_ЛОГИН/konny-krug.git
git branch -M main
git push -u origin main
```

Если GitHub просит пароль — используйте **Personal Access Token**:
GitHub → Settings → Developer settings → Personal access tokens → Tokens (classic) → Generate new token (scope `repo`),
затем `git push` и в качестве пароля вставьте токен.

## Вариант 3. Через GitHub CLI

```bash
gh auth login
cd konny-krug
gh repo create konny-krug --private --source=. --push
```

## После публикации

* Вкладка **Actions** соберёт APK при каждом коммите — готовый файл лежит в артефакте `konny-krug-apk`.
* Готовые сборки также лежат в папке `dist/` — их можно скачать с телефона и сразу установить.
* Чтобы раздать приложение коллеге: **Releases → Draft a new release** → прикрепите `konny-krug-1.0.apk`.
