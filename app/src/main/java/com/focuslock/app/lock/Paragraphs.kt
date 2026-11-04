package com.focuslock.app.lock

/**
 * Text you must type exactly to loosen a protection. Three of these are joined at random,
 * which comes to roughly 250-300 words. Edit freely: your own words work best.
 */
object Paragraphs {
    private val pool = listOf(
        "I am choosing to turn off a protection that I set up for myself on purpose. When I installed this app I knew that short videos were taking hours out of my days, hours I wanted for sleep, work, friends and the things I actually care about. Nothing has changed since then except that right now I feel a pull to scroll.",
        "The urge I feel at this moment is temporary. It usually fades within ten minutes if I do something else, like drinking a glass of water, stepping outside, or texting someone I like. If I still want to switch this off after typing every single word of this, then I will accept that it was a deliberate decision and not an impulse.",
        "Every reel is designed by very smart people to keep me watching the next one. That is not a flaw in me, it is how the product is built. Blocking it is not a punishment, it is simply refusing to play a game that I can never win. I would rather spend my attention on something I chose myself.",
        "Before I continue typing, I will ask myself honestly what I am avoiding right now. Is it boredom, stress, loneliness, or a task I do not want to start? Scrolling will not fix any of those. It will only push them a little later and leave me more tired when I finally come back to them.",
        "Tomorrow morning I will remember how this evening went. I can either remember that I kept my word to myself, or that I spent another hour watching clips I will not recall. Small promises kept are how trust in myself is built, one ordinary day at a time, and this is one of those days.",
        "If I genuinely need this turned off for a real reason, typing this paragraph is a small price and I will finish it calmly. If I do not have a real reason, the fact that this feels annoying is exactly the point. The friction is here to give my thoughtful self a chance to catch up with my impulsive self.",
        "Some of the best things I have done happened because I was bored enough to start them. Boredom is not an emergency. It is space. I can sit with it for a few minutes, notice what I actually want, and pick something that leaves me feeling better afterwards instead of worse and slightly empty.",
        "I am typing this slowly and carefully, letter by letter, because a single mistake sends me back to the start. That is deliberate. By the time I reach the end I will have spent several minutes thinking clearly, and whatever I decide then will be a real decision that I am willing to own completely.",
    )

    fun random(count: Int = 3): String = pool.shuffled().take(count).joinToString(" ")
}
