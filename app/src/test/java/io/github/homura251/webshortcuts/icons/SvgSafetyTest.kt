package io.github.homura251.webshortcuts.icons

import org.junit.Test

class SvgSafetyTest {
    @Test fun acceptsLocalGradientReferences() {
        SvgSafety.check("""<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24"><defs><linearGradient id="g"><stop stop-color="red"/></linearGradient></defs><path fill="url(#g)" d="M0 0h24v24z"/></svg>""")
    }

    @Test(expected = IllegalArgumentException::class) fun rejectsExternalEntity() {
        SvgSafety.check("""<!DOCTYPE svg [<!ENTITY xxe SYSTEM "file:///etc/passwd">]><svg><text>&xxe;</text></svg>""")
    }

    @Test(expected = IllegalArgumentException::class) fun rejectsExternalImage() {
        SvgSafety.check("""<svg><image href="https://tracker.example/icon.png"/></svg>""")
    }

    @Test(expected = IllegalArgumentException::class) fun rejectsCssNetworkReference() {
        SvgSafety.check("""<svg><path fill="url(https://example.com/mask.svg)"/></svg>""")
    }

    @Test(expected = IllegalArgumentException::class) fun rejectsCssImport() {
        SvgSafety.check("""<svg><style>@import 'https://tracker.example/style.css';</style></svg>""")
    }

    @Test(expected = IllegalArgumentException::class) fun rejectsIndirectRecursiveUse() {
        SvgSafety.check("""<svg><g id="a"><use href="#b"/></g><g id="b"><use href="#a"/></g></svg>""")
    }

    @Test(expected = IllegalArgumentException::class) fun rejectsDirectRecursiveUse() {
        SvgSafety.check("""<svg><g id="loop"><use href="#loop"/></g></svg>""")
    }
}
