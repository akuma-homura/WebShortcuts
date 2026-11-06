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

    @Test(expected = IllegalArgumentException::class, timeout = 2000) fun rejectsAcyclicExponentialUseExpansion() {
        val source = buildString {
            append("<svg><defs><g id='n0'><path d='M0 0h1v1z'/></g>")
            for (level in 1..10) {
                append("<g id='n$level'>")
                repeat(3) { append("<use href='#n${level - 1}'/>") }
                append("</g>")
            }
            append("</defs><use href='#n10'/></svg>")
        }
        // Only 45 source elements and no cycle: expansion still exceeds 59,000 path draws.
        SvgSafety.check(source)
    }

    @Test(expected = IllegalArgumentException::class, timeout = 2000) fun rejectsRepeatedLargePathWithoutAnElementExplosion() {
        val source = buildString {
            append("<svg><defs><path id='p' d='M0 0")
            repeat(3000) { append("L1 1 ") }
            append("'/></defs>")
            repeat(100) { append("<use href='#p'/>") }
            append("</svg>")
        }
        SvgSafety.check(source)
    }

    @Test fun acceptsReasonableSymbolReuse() {
        SvgSafety.check("""<svg><defs><g id="symbol"><path d="M0 0h1v1z"/></g></defs><use href="#symbol"/><use href="#symbol"/></svg>""")
    }

    @Test(expected = IllegalArgumentException::class) fun rejectsDuplicateIdsThatCouldResolveDifferentlyInTheRenderer() {
        SvgSafety.check("""<svg><g id="a"><use href="#a"/></g><path id="a" d="M0 0h1v1z"/></svg>""")
    }

    @Test(expected = IllegalArgumentException::class) fun rejectsDirectRecursiveUse() {
        SvgSafety.check("""<svg><g id="loop"><use href="#loop"/></g></svg>""")
    }
}
