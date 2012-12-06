wala-tag=HEAD
wala-git-repo=https://github.com/wala/WALA.git
wala-src-dir=wala-src
wala-patch=wala-trunk-2012-06-06.patch

.PHONY: patch-wala
patch-wala: wala-src
	cd $(wala-src-dir) && git apply ../$(wala-patch)
		$(RM)	$(wala-src-dir)/com.ibm.wala.cast/source/java/com/ibm/wala/cast/loader/CAstAbstractNativeLoader.java
		$(RM)	$(wala-src-dir)/com.ibm.wala.cast/source/java/com/ibm/wala/cast/tree/TranslatorToCAst.java

$(wala-src-dir):
	git clone $(wala-git-repo) $(wala-src-dir)

clean:
	rm -rf $(wala-src-dir)
