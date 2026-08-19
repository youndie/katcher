package ru.workinprogress.katcher.ui

import kotlinx.html.HEAD
import kotlinx.html.link
import kotlinx.html.script
import kotlinx.html.unsafe

fun HEAD.commonHead() {
    darkTheme()
    htmx()
    fonts()
    tailwind()
    favicon()
    dialogScript()
    fragmentErrorScript()
}

fun HEAD.favicon() {
    link {
        rel = "icon"
        type = "image/svg+xml"
        href = "/favicon.svg"
    }
}

/**
 * A fragment that failed to load has to say so, and the server never learns that its answer
 * did not arrive — so this is client-side by necessity. It renders the same three facts the
 * design asks for (what was requested, what came back, when) and a button that asks again.
 */
fun HEAD.fragmentErrorScript() {
    script {
        unsafe {
            val script =
                """
                window.katcherFragmentError = function (el, event, slotId) {
                  const body = document.getElementById(slotId + '-body');
                  if (!body) return;

                  const url = (event.detail && event.detail.pathInfo && event.detail.pathInfo.requestPath) || el.getAttribute('hx-get') || '';
                  const status = (event.detail && event.detail.xhr && event.detail.xhr.status) || 'no response';
                  const time = new Date().toTimeString().slice(0, 8);

                  body.innerHTML =
                    '<div class="px-4 py-4 flex items-center justify-between gap-4 flex-wrap">' +
                      '<div class="flex flex-col gap-1 min-w-0">' +
                        '<div class="text-sm font-medium">This list could not be loaded</div>' +
                        '<div class="text-xs font-mono text-muted-foreground break-all"></div>' +
                      '</div>' +
                      '<button class="inline-flex items-center justify-center h-8 px-3 text-xs font-medium border border-input bg-background cursor-pointer" ' +
                        'hx-get="' + url + '" hx-target="#' + slotId + '-body" hx-swap="innerHTML">Retry</button>' +
                    '</div>';

                  body.querySelector('.font-mono').textContent = 'GET ' + url + ' — ' + status + ', ' + time;
                  window.htmx && window.htmx.process(body);
                };
                """.trimIndent()
            +script
        }
    }
}

fun HEAD.htmx() {
    script(src = "https://cdn.jsdelivr.net/npm/htmx.org@2.0.8/dist/htmx.min.js") {}
}

fun HEAD.fonts() {
    link {
        rel = "preconnect"
        href = "https://fonts.googleapis.com"
    }

    link {
        rel = "preconnect"
        href = "https://fonts.gstatic.com"
        attributes["crossorigin"] = ""
    }
    link {
        rel = "stylesheet"
        href =
            "https://fonts.googleapis.com/css2?family=Outfit:wght@400;500;600;700&family=Source+Serif+4:wght@400;600;700&family=Fira+Code:wght@400;500;600&display=swap"
    }
}

fun HEAD.tailwind() {
    link(rel = "stylesheet", href = "/static/tailwind.css")
}

fun HEAD.darkTheme() {
    script {
        unsafe {
            val themeScript =
                """
                (function() {
                  document.documentElement.style.visibility = 'hidden';

                  try {
                    const saved = localStorage.getItem('theme');
                    const systemPrefersDark = window.matchMedia('(prefers-color-scheme: dark)').matches;
                    const theme = saved || (systemPrefersDark ? 'dark' : 'light');

                    if (theme === 'dark') {
                      document.documentElement.classList.add('dark');
                      document.documentElement.style.colorScheme = 'dark';
                    } else {
                      document.documentElement.classList.remove('dark');
                      document.documentElement.style.colorScheme = 'light';
                    }
                  } catch(e) {
                    document.documentElement.classList.remove('dark');
                    document.documentElement.style.colorScheme = 'light';
                  }

                  document.documentElement.style.visibility = 'visible';
                  
                   window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', e => {
                      if (e.matches) {
                          document.documentElement.classList.add('dark');
                          document.documentElement.style.colorScheme = 'dark';
                      } else {
                          document.documentElement.classList.remove('dark');
                          document.documentElement.style.colorScheme = 'light';
                      }
                  });
                })();
                """.trimIndent()
            +themeScript
        }
    }
}
