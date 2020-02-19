# pronto

a thin, lean & mean wrapper around protocol buffers that allow clojure users to use protoc generated POJOs as clojure maps.
it achieves it by generating a class at runtime that implements IPersistentMap by delegating storage to the POJO.