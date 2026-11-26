# R8 for release builds: shrinking, and optimizing what isn't kept.
#
# Nothing is renamed, so stack traces in "Copy log" stay readable. Everything,
# the app included, is shrunk and optimized, as BitChord does: Compose run
# unoptimized is markedly slower, the stutter on the first scrolls and the
# opening. Shrinking removes code nothing calls, the bulk of it unused Material
# icons, which is where the size goes.
#
# App code is statically reachable: serializers are called as X.serializer(),
# the JavaScript interface is covered by the rule below, and manifest
# components are kept by AGP. If a path turns up that R8 cannot see, keep that
# one class here rather than the whole app.
#
# The libraries that stream resolution reaches by reflection (NewPipe, Rhino,
# InnerTubeX, Ktor, protobuf) keep their rules where they belong, in
# :legato:source's consumer-rules.pro.
-dontobfuscate
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod,SourceFile,LineNumberTable,Exceptions
