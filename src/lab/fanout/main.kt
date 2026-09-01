package lab.fanout

import dev.botta.trantor.hosting.addModule
import dev.botta.trantor.web.application.WebApplication
import lab.fanout.web.TwitterFanoutWebModule

fun main() {
    val builder = WebApplication.builder { appName = "twitter-fanout-lab" }
    builder.addModule<TwitterFanoutWebModule>()
    builder.build().run()
}
