# Plan: building nullptr.nl project pages from markdown in git

Generate the project pages from markdown files kept in a git repo hosted elsewhere. The site builds from a chosen tag or branch, and the repo is cached locally so content is only downloaded when the remote commit has changed.

## Summary

**Recommendation:** use **Astro as a static site generator**, plus a short **fetch script that keeps a local clone of the content repo**.

- Git already does the hash comparison. `git ls-remote` tells you the commit a tag or branch points to without downloading anything.
- The output is plain HTML, CSS and images, so there is no PHP and no server process to run.
- The current host (WordPress 7.1 on LiteSpeed) can serve static files, so nothing has to move.

## Options considered

| Option | Content stored in | Runtime needs | Fit |
|---|---|---|---|
| **Astro (static)** + sync script | Markdown in git | None (static files) | **Best fit** |
| Hugo + Hugo Modules | Markdown in git (pulled from the repo, pinned to a tag or branch, cached) | None (static files). Needs Go at build time | Good alternative |
| EmDash | Database (structured JSON) | Node 22+, plus a database (SQLite, Postgres or Cloudflare D1) | Poor fit |
| WordPress (current) | Database | PHP + MySQL | What you want to leave |

**Why not EmDash:** EmDash is an Astro-based CMS (1.0 in September 2026). It stores content in a database rather than as markdown files in git, and it renders pages on the server by default. That defeats the point of keeping content as markdown in git, and it swaps PHP for a Node server plus a database.

**Why not Hugo (by default):** Hugo can pull content straight from another git repo, pinned to a tag or branch and cached, and it's a single binary. Its templates are clunkier than Astro's, and that feature needs Go installed at build time. Choose Hugo if you'd rather have fewer moving parts than nicer templates.

## Architecture

```
┌──────────────────────┐   git ls-remote (check SHA)   ┌────────────────────────┐
│ content repo         │ ◄──────────────────────────── │ site repo (Astro)      │
│ (off-site, markdown) │ ─── git fetch only if changed ►│  .content-cache/<ref>/ │
└──────────────────────┘                               │  astro build → dist/   │
                                                       └───────────┬────────────┘
                                                                   │ rsync / SFTP
                                                                   ▼
                                                        nullptr.nl (static HTML)
```

There are two repos:

1. **Content repo**: markdown and images only.
2. **Site repo**: Astro layouts, styles, the sync script and the build/deploy setup.

## 1. Content repo

```
content/
  projects/
    nowind/
      index.md                → /projects/nowind/
      cartridge.md            → /projects/nowind/cartridge/
      img/label-v2.jpg
```

Every page starts with a short header block (frontmatter):

```markdown
---
title: The cartridge
summary: Technical details of the Nowind Interface, how to use it with nwhostapp, firmware updates and troubleshooting.
tags: [Hardware, MSX, USB, Android]
order: 1
---
```

Conventions:

- The folder layout is the URL layout.
- Images sit next to the markdown and use relative links, so GitHub's own preview shows them too.
- **Tags** (for example `site-2026-10-24`) mark versions that are released and live.
- **Branches** (for example `draft`) are for preview builds.

## 2. Fetch-and-cache script

`scripts/sync-content.sh` in the site repo:

```sh
#!/bin/sh
set -eu
REPO=${CONTENT_REPO:-git@github.com:janwilmans/site-content.git}
REF=${1:-main}                         # tag or branch name
CACHE=.content-cache/$REF

# 1. Ask the remote which commit REF points to. This downloads no content.
REFS=$(git ls-remote "$REPO" "refs/heads/$REF" "refs/tags/$REF" "refs/tags/$REF^{}")
# For an annotated tag, use the commit behind it (the ^{} line).
REMOTE_SHA=$(echo "$REFS" | grep '\^{}$' | cut -f1 || true)
[ -n "$REMOTE_SHA" ] || REMOTE_SHA=$(echo "$REFS" | head -1 | cut -f1)
[ -n "$REMOTE_SHA" ] || { echo "ref '$REF' not found in $REPO" >&2; exit 1; }

# 2. Stop here if the cache already holds that commit.
if [ -f "$CACHE/.sha" ] && [ "$(cat "$CACHE/.sha")" = "$REMOTE_SHA" ]; then
  echo "content up to date: $REF @ $REMOTE_SHA"
  exit 0
fi

# 3. Otherwise fetch only what is missing (shallow) and check it out.
if [ ! -d "$CACHE/.git" ]; then
  mkdir -p "$CACHE"
  git -C "$CACHE" init -q
  git -C "$CACHE" remote add origin "$REPO"
fi
git -C "$CACHE" fetch -q --depth=1 origin "$REF"
git -C "$CACHE" checkout -q --force --detach FETCH_HEAD
git -C "$CACHE" clean -qfdx -e .sha
echo "$REMOTE_SHA" > "$CACHE/.sha"
echo "content updated: $REF @ $REMOTE_SHA"
```

How it behaves:

- **Content unchanged:** one network round trip, no download.
- **Content changed:** only the missing git objects are fetched.
- Each tag or branch gets its own cache folder, so switching between them doesn't throw away the cache.
- Add `.content-cache/` to `.gitignore`.

## 3. Astro site

**Build command** in `package.json`:

```json
"scripts": {
  "sync": "sh scripts/sync-content.sh ${CONTENT_REF:-main}",
  "build": "npm run sync && astro build",
  "dev": "npm run sync && astro dev"
}
```

**Content collection** in `src/content.config.ts`:

```ts
import { defineCollection, z } from 'astro:content';
import { glob } from 'astro/loaders';

const ref = process.env.CONTENT_REF ?? 'main';

const projects = defineCollection({
  loader: glob({ pattern: '**/*.md', base: `.content-cache/${ref}/content/projects` }),
  schema: z.object({
    title: z.string(),
    summary: z.string().optional(),
    tags: z.array(z.string()).default([]),
    order: z.number().default(100),
  }),
});

export const collections = { projects };
```

Astro's `glob()` loader keeps its own per-file cache, so pages whose files didn't change aren't processed again.

**Pages:**

- `src/pages/projects/[...slug].astro` renders every entry through one shared `ProjectLayout`.
- `src/pages/projects/index.astro` lists the projects. An overview page lists its subpages, sorted by `order`.
- Rebuild the current look as one layout: header, nav, the tag line ("Hardware · MSX · USB · Android"), the "← Back to …" link and the spec tables. Markdown tables cover the spec tables.
- Write the content commit SHA into the page footer (read `.content-cache/<ref>/.sha` at build time), so you can always tell which content version is live.

## 4. Build and deploy

**Option A: manual (simplest)**

```sh
CONTENT_REF=site-2026-10-24 npm run build
rsync -avz --delete dist/ user@nullptr.nl:public_html/projects-static/
# or, if the host only offers SFTP:  lftp -e "mirror -R --delete dist/ /public_html/..." sftp://...
```

**Option B: automated with GitHub Actions**

- The workflow runs when triggered by hand (`workflow_dispatch`) with a `ref` input, or on a `repository_dispatch` event that the content repo sends when you push a tag.
- It caches `.content-cache` with `actions/cache`, keyed on the remote SHA (get it from `git ls-remote` in an earlier step), so the hash check works in CI too.
- It deploys with rsync or SFTP to the current host. Store the key in GitHub Secrets.
- Alternative hosts: Cloudflare Pages or GitHub Pages (free, HTTPS included), if you'd rather drop the hosting.

## 5. Migrating from WordPress

1. Export the projects section from WordPress (Tools → Export) and convert it with `wordpress-export-to-markdown`. Clean the result up by hand.
2. Download the images the pages use into the `img/` folder next to each page.
3. **Redirects:** keep the old URLs working with 301 redirects from `/index.php/projects/...` to `/projects/...`. Put them in `.htaccess` on LiteSpeed, or in a `_redirects` file on Cloudflare Pages.
4. **The blog (decision needed):**
   - *Phase 1:* the project pages are served statically, and WordPress keeps serving `/blog/` and the rest.
   - *Phase 2 (optional):* move the blog to the same markdown setup as a second collection (`blog`), then retire WordPress, along with its updates, Wordfence and the plugin upkeep.

## 6. Order of work

| # | Step | Done when |
|---|---|---|
| 1 | Create the content repo and move the Nowind section into it | Markdown and images look right in GitHub's preview |
| 2 | Scaffold the Astro site and add `sync-content.sh` | A second `npm run build` reports "up to date" |
| 3 | Build the layout to match the current look | Local pages look like the live pages |
| 4 | Add redirects and deploy next to WordPress | Old and new URLs both work |
| 5 | Add the GitHub Action | Pushing a tag deploys the site |
| 6 | Decide on the blog | — |

## Edge cases

- **A tag and a branch with the same name:** use a naming convention (`site-*` for tags) to avoid ambiguity.
- **A tag moved with force-push:** `ls-remote` reports the new SHA, so the cache is refreshed. This works as intended.
- **Private content repo:** use a deploy key (read-only SSH key), both locally and in CI.
- **Remote unreachable during a build:** if `ls-remote` fails, the script exits non-zero. If you want, add a `--offline` flag that builds from whatever is in the cache.

## References

- [EmDash CMS review](https://www.bitdoze.com/md/emdash-cms-review.md)
- [EmDash & Astro (Astro docs)](https://docs.astro.build/it/guides/cms/emdash/)
- [EmDash on GitHub](https://github.com/emdash-cms/emdash)
- [astro-git-repo-loader](https://jsr.io/@jakew/astro-git-repo-loader/score), a ready-made loader that pulls content from a git host, as an alternative to the sync script
- [Hugo: mounting different branches/tags from the same repository](https://discourse.gohugo.io/t/mounting-different-branches-tags-from-the-same-repository/55849)
- [Hugo: rendering content from remote repositories](https://discourse.gohugo.io/t/how-to-render-content-from-remote-repositories-using-module-imports-mount-in-v0-105-0/41278)
