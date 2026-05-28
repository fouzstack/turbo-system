package com.nexus;

import android.webkit.WebView;

import androidx.annotation.NonNull;

/**
 * Injects the Nexus JavaScript runtime into the WebView.
 * Prevents multiple simultaneous injections.
 */
final class NexusRuntimeInjector {

    private final WebView webView;
    private final String jsName;
    private volatile boolean injecting = false;

    NexusRuntimeInjector(@NonNull WebView webView, @NonNull String jsName) {
        this.webView = webView;
        this.jsName = jsName;
    }

    void inject() {
        if (injecting) return;

        injecting = true;

        String script = getNexusRuntimeScript();
        String scriptWithName = script.replace("__NEXUS_NAME__", jsName);

        webView.post(() -> {
            try {
                webView.evaluateJavascript(scriptWithName, result -> {
                    NexusLog.d("RuntimeInjector", "Runtime injected. Result: " + result);
                    injecting = false;
                });
            } catch (Exception e) {
                NexusLog.e("RuntimeInjector", "Failed to inject runtime", e);
                injecting = false;
            }
        });
    }

    private static String getNexusRuntimeScript() {
        return NEXUS_RUNTIME_JS;
    }

    /**
     * Nexus JavaScript Runtime v2.1.0
     *
     * Provides: call(), on(), off(), once() with promise-based RPC.
     * Internal methods: _res(), _rej(), _emt()
     * Auto-cleanup of orphaned callbacks after 5 minutes.
     */
    private static final String NEXUS_RUNTIME_JS =
        "(function(){" +
            "if(window.Nexus&&window.Nexus._init)return;" +
            "var n=window.Nexus||{};" +
            "n._c=n._c||{};" +
            "n._e=n._e||{};" +
            "n._id=n._id||0;" +
            "n._gid=function(){return'cb_'+ ++n._id+'_'+Date.now()};" +
            "n.call=function(m,p){" +
                "return new Promise(function(r,j){" +
                    "var i=n._gid();" +
                    "n._c[i]={r:r,j:j,t:Date.now()};" +
                    "try{__NEXUS_NAME__.call(m,JSON.stringify(p||{}),i)}catch(e){" +
                        "delete n._c[i];j({code:'BRIDGE_ERROR',message:'Error: '+e.message})}" +
                "})};" +
            "n.on=function(e,f){" +
                "if(typeof f!=='function')return null;" +
                "var i=n._gid();" +
                "n._c[i]=f;" +
                "if(!n._e[e])n._e[e]=[];" +
                "n._e[e].push(i);" +
                "try{__NEXUS_NAME__.on(e,i)}catch(e){}" +
                "return i};" +
            "n.off=function(e,i){" +
                "if(n._e[e]){n._e[e]=n._e[e].filter(function(id){return id!==i});" +
                "if(n._e[e].length===0)delete n._e[e]}" +
                "delete n._c[i];" +
                "try{__NEXUS_NAME__.off(e,i)}catch(e){}};" +
            "n.once=function(e,f){" +
                "var i=n.on(e,function(d){" +
                    "f(d);n.off(e,i);" +
                "});return i};" +
            "n._res=function(i,r){" +
                "var c=n._c[i];" +
                "if(c&&typeof c.r==='function'){c.r(r);delete n._c[i]}};" +
            "n._rej=function(i,e){" +
                "var c=n._c[i];" +
                "if(c&&typeof c.j==='function'){c.j(e);delete n._c[i]}};" +
            "n._emt=function(e,i,d){" +
                "var l=n._c[i];" +
                "if(typeof l==='function'){try{l(d)}catch(e){console.error('[Nexus]',e)}}};" +
            "setInterval(function(){" +
                "var now=Date.now();" +
                "for(var i in n._c){" +
                    "if(n._c[i].t&&now-n._c[i].t>300000){" +
                        "delete n._c[i];" +
                    "}" +
                "}" +
            "},60000);" +
            "n._init=true;" +
            "n.v='2.1.0';" +
            "window.Nexus=n;" +
        "})();";
}