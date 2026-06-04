package com.example.blue_book.router

object RouterPath {
    object Auth {
        const val ENTRY    = "/feature/auth/entry"
        const val LOGIN    = "/feature/auth/login"
        const val REGISTER = "/feature/auth/register"
    }
    object Main {
        const val TABS = "/feature/main"
    }
    object Home {
        const val MAIN          = "/feature/home"
        const val SEARCH        = "/feature/home/search"
        const val SEARCH_RESULT = "/feature/home/search/result"
    }
    object Video {
        const val TAB    = "/feature/video"
        const val PLAYER = "/feature/video/player"
    }
    object Message {
        const val MAIN = "/feature/message"
    }
    object Mine {
        const val MAIN    = "/feature/mine"
        const val PROFILE = "/feature/mine/profile"
    }
    object Image {
        const val PICKER = "/feature/image/picker"
    }
}
