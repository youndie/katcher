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
    dialogScript()
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
            +
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
        }
    }
}
