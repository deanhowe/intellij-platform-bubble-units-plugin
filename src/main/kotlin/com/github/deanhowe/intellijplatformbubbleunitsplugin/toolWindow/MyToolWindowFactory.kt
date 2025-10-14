package com.github.deanhowe.intellijplatformbubbleunitsplugin.toolWindow

import com.github.deanhowe.intellijplatformbubbleunitsplugin.services.BubbleSettingsService
import com.github.deanhowe.intellijplatformbubbleunitsplugin.services.BubbleReportWatcher
import com.intellij.ide.BrowserUtil
import com.intellij.ide.ui.LafManagerListener
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.Logger
import com.github.deanhowe.intellijplatformbubbleunitsplugin.util.Logging
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.jcef.JBCefJSQuery
import org.cef.handler.CefLoadHandlerAdapter
import org.cef.handler.CefDisplayHandlerAdapter
import org.cef.browser.CefFrame
import com.github.deanhowe.intellijplatformbubbleunitsplugin.MyBundle
import com.github.deanhowe.intellijplatformbubbleunitsplugin.toolWindow.attachConsoleLogger
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel

class MyToolWindowFactory : ToolWindowFactory {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val myToolWindow = MyToolWindow(project, toolWindow)
        val contentManager = toolWindow.contentManager
        val content = contentManager.factory.createContent(myToolWindow.getContent(), null, false)
        content.setDisposer(myToolWindow)
        contentManager.addContent(content)
    }

    override fun shouldBeAvailable(project: Project) = true

    class MyToolWindow(private val project: Project, private val toolWindow: ToolWindow) : com.intellij.openapi.Disposable {

        private val settings = BubbleSettingsService.getInstance(project)
        private var browser: JBCefBrowser? = null
                private var saveQuery: JBCefJSQuery? = null
                private var notifyQuery: JBCefJSQuery? = null
                private var consoleQuery: JBCefJSQuery? = null
        private val panel = JPanel(BorderLayout())
        private val contentPanel = JPanel(BorderLayout())
        
        // Tracks export operations for consolidated notifications
        private data class ExportOperation(val type: String, val path: String)
        private val pendingExports = mutableListOf<ExportOperation>()
        private val exportLock = Object()
        private val exportDebounceMs = 300L
        private var exportNotificationTask: java.util.concurrent.ScheduledFuture<*>? = null
        private val exportScheduler = com.intellij.util.concurrency.AppExecutorUtil.getAppScheduledExecutorService()

        init {
            // Toolbar at the top
            panel.add(createToolbar(), BorderLayout.NORTH)
            panel.add(contentPanel, BorderLayout.CENTER)

            // Initialize the browser/content area
            createBrowser()

            // Removed deprecated ToolWindowManagerListener.stateChanged usage.
            // Browser is created once and remains; rely on BubbleReportWatcher to trigger reloads as needed.

            // Subscribe to settings changes and junit-report.xml changes and reload the panel when it changes
            val connection = project.messageBus.connect(project)
            connection.subscribe(
                BubbleSettingsService.SETTINGS_CHANGED,
                object : BubbleSettingsService.Companion.SettingsListener {
                    override fun bubbleSettingsChanged() {
                        loadUrl()
                    }
                }
            )
            connection.subscribe(
                BubbleReportWatcher.TOPIC,
                object : BubbleReportWatcher.Listener {
                    private var pending = false
                    private val debounceMs = 750L
                    private val scheduler = com.intellij.util.concurrency.AppExecutorUtil.getAppScheduledExecutorService()
                    override fun junitReportChanged() {
                        // Debounce rapid sequences of file changes to avoid janky reloads
                        synchronized(this) {
                            if (pending) return
                            pending = true
                        }
                        scheduler.schedule({
                            try {
                                loadUrl()
                            } finally {
                                synchronized(this) { pending = false }
                            }
                        }, debounceMs, java.util.concurrent.TimeUnit.MILLISECONDS)
                    }
                }
            )

            // Reload on IDE theme (Look and Feel) changes using public API
            // Subscribe to Look and Feel (theme) changes on the APPLICATION message bus.
            // Using the project bus here would not deliver LafManager events.
            val appConnection = com.intellij.openapi.application.ApplicationManager.getApplication().messageBus.connect(project)
            appConnection.subscribe(
                LafManagerListener.TOPIC,
                LafManagerListener {
                    settings.invalidateCache()
                    loadUrl()
                }
            )

            // Ensure the watcher service is initialized
            BubbleReportWatcher.getInstance(project)
        }

        fun getContent(): JPanel {
            return panel
        }

        override fun dispose() {
            disposeBrowser()
            cancelExportNotification()
        }
        
        private fun cancelExportNotification() {
            synchronized(exportLock) {
                exportNotificationTask?.cancel(false)
                exportNotificationTask = null
                pendingExports.clear()
            }
        }
        
        private fun addExport(type: String, path: String) {
            synchronized(exportLock) {
                pendingExports.add(ExportOperation(type, path))
                
                // Cancel any pending notification
                exportNotificationTask?.cancel(false)
                
                // Schedule a new notification after debounce period
                exportNotificationTask = exportScheduler.schedule({
                    showConsolidatedExportNotification()
                }, exportDebounceMs, java.util.concurrent.TimeUnit.MILLISECONDS)
            }
        }
        
        private fun showConsolidatedExportNotification() {
            val exports = synchronized(exportLock) {
                val copy = pendingExports.toList()
                pendingExports.clear()
                exportNotificationTask = null
                copy
            }
            
            if (exports.isEmpty()) return
            
            // Build a consolidated message
            val message = buildString {
                if (exports.size == 1) {
                    val export = exports.first()
                    append("Saved ${export.type} → ${export.path}")
                } else {
                    append("Saved ${exports.size} files:")
                    exports.take(3).forEach { export ->
                        append("\n• ${export.type}: ${export.path}")
                    }
                    if (exports.size > 3) {
                        append("\n• ...and ${exports.size - 3} more")
                    }
                }
            }
            
            try {
                com.intellij.notification.NotificationGroupManager.getInstance()
                    .getNotificationGroup("BubbleUnits")
                    .createNotification(
                        MyBundle.message("bubbleToolWindowTitle"),
                        message,
                        com.intellij.notification.NotificationType.INFORMATION
                    ).notify(project)
            } catch (_: Exception) {}
        }

        // Create JBCefJSQuery without directly calling deprecated APIs.
        // Tries newer create(CefBrowser) overload; falls back to deprecated create(JBCefBrowser) via reflection.
        private fun createJsQueryCompat(br: JBCefBrowser): JBCefJSQuery {
            try {
                val clazz = JBCefJSQuery::class.java
                val cefClass = Class.forName("org.cef.browser.CefBrowser")
                val method = clazz.getMethod("create", cefClass)
                val cefBrowserObj = br.cefBrowser
                val created = method.invoke(null, cefBrowserObj)
                if (created is JBCefJSQuery) return created
            } catch (_: Throwable) {
                // ignore and try deprecated overload
            }
            try {
                val clazz = JBCefJSQuery::class.java
                val jbClass = Class.forName("com.intellij.ui.jcef.JBCefBrowser")
                val method = clazz.getMethod("create", jbClass)
                @Suppress("DEPRECATION", "DEPRECATION_ERROR")
                val created = method.invoke(null, br)
                if (created is JBCefJSQuery) return created
            } catch (e: Throwable) {
                throw RuntimeException("Failed to create JBCefJSQuery via reflection", e)
            }
            // Should not reach here
            throw IllegalStateException("JBCefJSQuery.create overloads not found")
        }

        private fun createToolbar(): JComponent {
            val group = DefaultActionGroup()

            // Reload action
            group.add(object : AnAction(
                MyBundle.message("toolbar.reload"),
                MyBundle.message("toolbar.reload.description"),
                null
            ) {
                override fun actionPerformed(e: AnActionEvent) {
                    loadUrl()
                }
            })

            // Open in Browser action
            group.add(object : AnAction(
                MyBundle.message("toolbar.openInBrowser"),
                MyBundle.message("toolbar.openInBrowser.description"),
                null
            ) {
                override fun actionPerformed(e: AnActionEvent) {
                    val app = com.intellij.openapi.application.ApplicationManager.getApplication()
                    app.executeOnPooledThread {
                        try {
                            val targetUrl = settings.url
                            // Always export HTML to snapshot dir as phpunit-bubble-report.html, then open that file in the browser.
                            val browseUrl = try {
                                val dir = try { settings.getOrCreateSnapshotDir() } catch (_: Exception) { null }
                                val outFile: java.io.File = if (dir != null && dir.canWrite()) {
                                    java.io.File(dir, "phpunit-bubble-report.html")
                                } else {
                                    java.io.File.createTempFile("phpunit-bubble-report-", ".html")
                                }

                                fun write(bytes: ByteArray) {
                                    outFile.writeBytes(bytes)
                                    try {
                                        com.intellij.notification.NotificationGroupManager.getInstance()
                                            .getNotificationGroup("BubbleUnits")
                                            .createNotification(
                                                MyBundle.message("bubbleToolWindowTitle"),
                                                "Saved html → ${outFile.absolutePath}",
                                                com.intellij.notification.NotificationType.INFORMATION
                                            ).notify(project)
                                    } catch (_: Exception) {}
                                }

                                when {
                                    targetUrl.startsWith("data:text/html", ignoreCase = true) -> {
                                        val comma = targetUrl.indexOf(',')
                                        if (comma > 0) {
                                            val meta = targetUrl.substring(0, comma)
                                            val payload = targetUrl.substring(comma + 1)
                                            val bytes: ByteArray = if (meta.contains(";base64", ignoreCase = true)) {
                                                java.util.Base64.getDecoder().decode(payload)
                                            } else {
                                                java.net.URLDecoder.decode(payload, java.nio.charset.StandardCharsets.UTF_8)
                                                    .toByteArray(java.nio.charset.StandardCharsets.UTF_8)
                                            }
                                            write(bytes)
                                        } else {
                                            // malformed data URL; write minimal page that redirects
                                            val html = """
                                                <!doctype html><meta charset=\"utf-8\"><title>Bubble Units</title>
                                                <meta http-equiv=\"refresh\" content=\"0;url='${targetUrl.replace("\"","%22")}'\">
                                                <p>Redirecting to bubble report… <a href=\"${targetUrl.replace("\"","%22")}\">Open</a></p>
                                            """.trimIndent()
                                            write(html.toByteArray(Charsets.UTF_8))
                                        }
                                        outFile.toURI().toString()
                                    }
                                    targetUrl.startsWith("file:") -> {
                                        try {
                                            val src = java.nio.file.Paths.get(java.net.URI(targetUrl))
                                            val bytes = java.nio.file.Files.readAllBytes(src)
                                            write(bytes)
                                            outFile.toURI().toString()
                                        } catch (ex: Exception) {
                                            Logging.warn(project, MyToolWindowFactory::class.java, "Failed to copy local HTML; falling back to open original", ex)
                                            targetUrl
                                        }
                                    }
                                    else -> {
                                        // For http/https or other schemes, write a tiny HTML that redirects to the target
                                        val html = """
                                            <!doctype html><meta charset=\"utf-8\"><title>Bubble Units</title>
                                            <meta http-equiv=\"refresh\" content=\"0;url='${targetUrl.replace("\"","%22")}'\">
                                            <p>Opening bubble report in your browser… <a href=\"${targetUrl.replace("\"","%22")}\">Continue</a></p>
                                        """.trimIndent()
                                        write(html.toByteArray(Charsets.UTF_8))
                                        outFile.toURI().toString()
                                    }
                                }
                            } catch (ex: Exception) {
                                // Fall back to original URL if decoding/writing fails
                                Logging.warn(project, MyToolWindowFactory::class.java, "Failed to export HTML for external browser; falling back", ex)
                                targetUrl
                            }
                            app.invokeLater({
                                try {
                                    BrowserUtil.browse(browseUrl)
                                } catch (ex: Exception) {
                                    Logging.warn(project, MyToolWindowFactory::class.java, "Failed to open browser", ex)
                                    Messages.showErrorDialog(
                                        project,
                                        MyBundle.message("bubbleToolWindow.openInBrowser.error"),
                                        MyBundle.message("bubbleToolWindowTitle")
                                    )
                                }
                            })
                        } catch (ex: Exception) {
                            Logging.warn(project, MyToolWindowFactory::class.java, "Failed to resolve URL for external browser", ex)
                            app.invokeLater({
                                Messages.showErrorDialog(
                                    project,
                                    MyBundle.message("bubbleToolWindow.openInBrowser.error"),
                                    MyBundle.message("bubbleToolWindowTitle")
                                )
                            })
                        }
                    }
                }
            })

            // Reinject Bridge action: re-inject JS bridge and re-bind export buttons without reloading the page
            group.add(object : AnAction(
                "Reinject Bridge",
                "Re-inject the Bubble Units JS bridge and re-bind export buttons",
                null
            ) {
                override fun actionPerformed(e: AnActionEvent) {
                    val br = browser ?: return
                    try {
                        // Log warnings if queries are null
                        if (saveQuery == null) {
                            Logging.warn(project, MyToolWindowFactory::class.java, "saveQuery is null - export bridge cannot be injected")
                        }
                        if (notifyQuery == null) {
                            Logging.warn(project, MyToolWindowFactory::class.java, "notifyQuery is null - notification bridge cannot be injected")
                        }
                        if (consoleQuery == null) {
                            Logging.warn(project, MyToolWindowFactory::class.java, "consoleQuery is null - console bridge cannot be injected")
                        }
                        
                        // Execute inject() code to define the bridge functions
                        // No need to delete/undefine anything since we're reinjecting over existing properties
                        val injectFn = (saveQuery?.inject("__bubbleSave_send") ?: "") + ";\n" +
                            (notifyQuery?.inject("__bubbleNotify_bridge") ?: "") + ";\n" +
                            (consoleQuery?.inject("__bubbleConsole_bridge") ?: "") + ";\n"
                        
                        // Execute inject code first
                        br.cefBrowser.executeJavaScript(injectFn, br.cefBrowser.url, 0)
                        
                        // Provide fallback implementations for bridges that failed to inject
                        val fallbackJs = """
                            // Fallback for notification bridge if it failed to inject
                            if (typeof window.__bubbleNotify_bridge !== 'function') {
                                console.info('[BubbleUnits] Creating fallback for __bubbleNotify_bridge');
                                window.__bubbleNotify_bridge = function(msg) {
                                    console.info('[BubbleUnits] Fallback notification: ' + msg);
                                    try {
                                        location.hash = '#__buNotify=' + encodeURIComponent(String(msg||'')) + '&t=' + Date.now();
                                        return true;
                                    } catch (e) {
                                        console.error('[BubbleUnits] Fallback notification failed:', e);
                                        return false;
                                    }
                                };
                            }
                        """.trimIndent()
                        br.cefBrowser.executeJavaScript(fallbackJs, br.cefBrowser.url, 0)
                        
                        // Verify bridges were injected
                        val verifyBridges = """
                            try {
                              console.info('[BubbleUnits] [Reinject] Bridge verification:');
                              console.info('  __bubbleSave_send: ' + typeof window.__bubbleSave_send);
                              console.info('  __bubbleNotify_bridge: ' + typeof window.__bubbleNotify_bridge);
                              console.info('  __bubbleConsole_bridge: ' + typeof window.__bubbleConsole_bridge);
                            } catch (e) { console.error('[BubbleUnits] [Reinject] Failed to verify bridges:', e); }
                        """.trimIndent()
                        br.cefBrowser.executeJavaScript(verifyBridges, br.cefBrowser.url, 0)
                        
                        val js = """
                            try {
                              (function(){
                                // Ensure notify alias exists, looking up bridge dynamically on each call
                                window.__bubbleNotify = function(msg){
                                  try {
                                    var bridge = (typeof window !== 'undefined' && typeof window.__bubbleNotify_bridge === 'function') ? window.__bubbleNotify_bridge : null;
                                    if (bridge) bridge(String(msg==null?'' : msg));
                                  } catch(e){}
                                };
                              })();
                            } catch (e) {}
                            (function(){
                              // Define __bubbleSave if missing and provide chunking sender
                              if (typeof window !== 'undefined' && typeof window.__bubbleSave !== 'function') {
                                function __bubbleSendRaw(s){
                                  try { if (typeof window !== 'undefined' && typeof window.__bubbleSave_send === 'function') { window.__bubbleSave_send(s); return true; } } catch(e){}
                                  return false;
                                }
                                window.__bubbleSave = (function(){
                                  var MAX = 24000;
                                  function sendRaw(s){ try{ if(!__bubbleSendRaw(s)){ try{ if (typeof window.__bubbleNotify==='function') window.__bubbleNotify('Export bridge is not ready — try Reload'); }catch(e2){} } }catch(e){} }
                                  function sendChunked(kind,name,b64){ try{ var id=Math.random().toString(36).slice(2); sendRaw('BEGIN\t'+id+'\t'+kind+'\t'+name); for(var i=0;i<b64.length;i+=MAX){ sendRaw('CHUNK\t'+id+'\t'+b64.slice(i,i+MAX)); } sendRaw('END\t'+id);}catch(e){}}
                                  return function(payload){ try{ if(typeof payload!=='string') return; if(payload.length<=MAX){ sendRaw(payload); return;} var f=payload.indexOf('\t'); if(f<0) return; var s=payload.indexOf('\t',f+1); if(s<0) return; var kind=payload.slice(0,f); var name=payload.slice(f+1,s); var b64=payload.slice(s+1); if(b64.length<=MAX){ sendRaw(payload); return;} sendChunked(kind,name,b64);}catch(e){} };
                                })();
                              }
                              function serializeSvg(){
                                var el=document.querySelector('#bubbles svg')||document.querySelector('svg'); if(!el) return null; var copy=el.cloneNode(true);
                                try{ var r=getComputedStyle(document.documentElement); var css='.bubbles circle.success{fill:'+r.getPropertyValue('--color-success').trim()+';}' + '.bubbles circle.failed{fill:'+r.getPropertyValue('--color-failed').trim()+';}' + '.bubbles circle.error{fill:'+r.getPropertyValue('--color-error').trim()+';}' + '.bubbles circle.skipped{fill:'+r.getPropertyValue('--color-warning').trim()+';}'; var styleEl=document.createElement('style'); styleEl.textContent=css; copy.insertBefore(styleEl, copy.firstChild);}catch(e){}
                                var s=new XMLSerializer().serializeToString(copy); if(!/^<svg[\s\S]*xmlns=/.test(s)) s=s.replace('<svg','<svg xmlns=\"http://www.w3.org/2000/svg\"'); return s;
                              }
                              function toPng(svgText){ return new Promise(function(resolve,reject){ try{ var img=new Image(); img.onload=function(){ var c=document.createElement('canvas'); c.width=img.width; c.height=img.height; var ctx=c.getContext('2d'); ctx.drawImage(img,0,0); resolve(c.toDataURL('image/png').split(',')[1]); }; img.onerror=reject; img.src='data:image/svg+xml;charset=utf-8,'+encodeURIComponent(svgText);}catch(e){reject(e);} }); }
                              function buNotify(msg){
                                try { if (typeof window.__bubbleNotify==='function') { window.__bubbleNotify(msg); return; } } catch(e){}
                                try { location.hash = '#__buNotify=' + encodeURIComponent(String(msg||'')) + '&t=' + Date.now(); } catch(e){}
                              }
                              function buSend(kind,name,b64){
                                try { if (typeof window.__bubbleSave==='function') { window.__bubbleSave(kind+'\t'+name+'\t'+b64); return; } } catch(e) {}
                                try { location.hash = '#__buSave=' + encodeURIComponent(kind+'|'+name) + '&d=' + b64; } catch(e){}
                              }
                              // Provide a stable window.__bubble API used by bubbles.js
                              try {
                                window.__bubble = window.__bubble || {};
                                window.__bubble.save = function(kind, name, b64){
                                  try { buSend(String(kind||'file'), String(name||'snapshot'), String(b64||'')); return Promise.resolve('OK'); } catch(e){ return Promise.reject(e); }
                                };
                                window.__bubble.exportSvg = function(name, b64){ return window.__bubble.save('svg', name, b64); };
                                window.__bubble.exportJson = function(name, b64){ return window.__bubble.save('json', name, b64); };
                                window.__bubble.exportPng = function(name, b64){ return window.__bubble.save('png', name, b64); };
                              } catch(e) {}
                              function bindIfPresent(el, listener){ try{ if(!el) return; if(el.getAttribute('data-bu-bound')==='1') return; el.setAttribute('data-bu-bound','1'); el.addEventListener('click', listener, true);}catch(e){} }
                              function tryBindButtons(){
                                try {
                                  var svgBtn=document.getElementById('svg_download_link');
                                  var pngBtn=document.getElementById('png_download_link');
                                  var jsonBtn=document.getElementById('json_report_download_link');
                                  bindIfPresent(svgBtn, function(ev){ ev.preventDefault(); var s=serializeSvg(); if(!s){ try{ window.__bubbleNotify && window.__bubbleNotify('No SVG found to export'); }catch(e){} return;} var b=btoa(unescape(encodeURIComponent(s))); buSend('svg','phpunit-bubble-report.svg', b); });
                                  bindIfPresent(pngBtn, async function(ev){ ev.preventDefault(); var s=serializeSvg(); if(!s){ try{ window.__bubbleNotify && window.__bubbleNotify('No SVG found to export'); }catch(e){} return;} var b=await toPng(s); buSend('png','phpunit-bubble-report.png', b); });
                                  bindIfPresent(jsonBtn, function(ev){ ev.preventDefault(); var data={circles:Array.from(document.querySelectorAll('#bubbles svg circle')).map(function(c){ return { cx:c.getAttribute('cx'), cy:c.getAttribute('cy'), r:c.getAttribute('r'), cls:c.getAttribute('class') }; })}; var txt=JSON.stringify(data,null,2); var b=btoa(unescape(encodeURIComponent(txt))); buSend('json','phpunit-bubble-report.json', b); });
                                } catch(e) {}
                              }
                              tryBindButtons();
                              try { if (typeof window.__bubbleNotify==='function') window.__bubbleNotify('Bridge reinjected'); } catch(e){}
                            })();
                        """.trimIndent()
                        br.cefBrowser.executeJavaScript(js, br.cefBrowser.url, 0)
                        val delegateJs = """
                            (function(){
                              try {
                                if (!window.__buDelegated) {
                                  window.__buDelegated = true;
                                  function __buSerializeSvg(){
                                    try {
                                      var el = document.querySelector('#bubbles svg') || document.querySelector('svg');
                                      if(!el) return null;
                                      var copy = el.cloneNode(true);
                                      try {
                                        var r = getComputedStyle(document.documentElement);
                                        var css = '.bubbles circle.success{fill:'+r.getPropertyValue('--color-success').trim()+';}' +
                                                  '.bubbles circle.failed{fill:'+r.getPropertyValue('--color-failed').trim()+';}' +
                                                  '.bubbles circle.error{fill:'+r.getPropertyValue('--color-error').trim()+';}' +
                                                  '.bubbles circle.skipped{fill:'+r.getPropertyValue('--color-warning').trim()+';}';
                                        var styleEl=document.createElement('style'); styleEl.textContent=css; copy.insertBefore(styleEl, copy.firstChild);
                                      } catch(e){}
                                      var s = new XMLSerializer().serializeToString(copy);
                                      if(!/^<svg[\s\S]*xmlns=/.test(s)) s = s.replace('<svg','<svg xmlns="http://www.w3.org/2000/svg"');
                                      return s;
                                    } catch(e){ return null; }
                                  }
                                  function __buToPng(svgText){ return new Promise(function(resolve,reject){ try{ var img=new Image(); img.onload=function(){ var c=document.createElement('canvas'); c.width=img.width; c.height=img.height; var ctx=c.getContext('2d'); ctx.drawImage(img,0,0); resolve(c.toDataURL('image/png').split(',')[1]); }; img.onerror=reject; img.src='data:image/svg+xml;charset=utf-8,'+encodeURIComponent(svgText);}catch(e){reject(e);} }); }
                                  function __buSend(kind,name,b64){
                                    try {
                                      if (typeof window.__bubbleSave==='function') {
                                        window.__bubbleSave(kind+'\t'+name+'\t'+b64);
                                      }
                                    } catch(e) { console.error(e); }
                                  }
                                  document.addEventListener('click', function(ev){
                                    try {
                                      var t = ev.target && ev.target.closest ? ev.target.closest('#svg_download_link, #png_download_link, #json_report_download_link') : null;
                                      if (!t) return;
                                      ev.preventDefault();
                                      var id = (t.id||'').toLowerCase();
                                      if (id === 'svg_download_link') {
                                        var s = __buSerializeSvg(); if(!s){ try{ window.__bubbleNotify && window.__bubbleNotify('No SVG found to export'); }catch(e){}; return; }
                                        var b = btoa(unescape(encodeURIComponent(s)));
                                        __buSend('svg','phpunit-bubble-report.svg', b);
                                      } else if (id === 'png_download_link') {
                                        var s2 = __buSerializeSvg(); if(!s2){ try{ window.__bubbleNotify && window.__bubbleNotify('No SVG found to export'); }catch(e){}; return; }
                                        __buToPng(s2).then(function(b){ __buSend('png','phpunit-bubble-report.png', b); }).catch(function(){});
                                      } else if (id === 'json_report_download_link') {
                                        var data = {circles: Array.from(document.querySelectorAll('#bubbles svg circle')).map(function(c){ return { cx:c.getAttribute('cx'), cy:c.getAttribute('cy'), r:c.getAttribute('r'), cls:c.getAttribute('class') }; })};
                                        var txt = JSON.stringify(data, null, 2);
                                        var b3 = btoa(unescape(encodeURIComponent(txt)));
                                        __buSend('json','phpunit-bubble-report.json', b3);
                                      }
                                    } catch(e) { try { console.error(e); } catch(ex) {} }
                                  }, true);
                                }
                              } catch(e) {}
                            })();
                        """.trimIndent()
                        br.cefBrowser.executeJavaScript(delegateJs, br.cefBrowser.url, 0)
                    } catch (_: Exception) {
                        // ignore reinjection errors
                    }
                }
            })

            val toolbar = ActionManager.getInstance().createActionToolbar("BubbleUnits.Toolbar", group, true)
            toolbar.targetComponent = panel
            return toolbar.component
        }

        private fun createBrowser() {
            val app = com.intellij.openapi.application.ApplicationManager.getApplication()
            val headless = app.isHeadlessEnvironment
            val jcefSupported = com.intellij.ui.jcef.JBCefApp.isSupported()
            contentPanel.removeAll()
            if (headless || !jcefSupported) {
                val msg = MyBundle.message("bubbleToolWindow.jcefNotSupported")
                val area = javax.swing.JTextArea(msg).apply {
                    isEditable = false
                    lineWrap = true
                    wrapStyleWord = true
                    border = javax.swing.BorderFactory.createEmptyBorder(8, 8, 8, 8)
                }
                contentPanel.add(javax.swing.JScrollPane(area), BorderLayout.CENTER)
                contentPanel.revalidate()
                contentPanel.repaint()
                return
            }

            browser = JBCefBrowser()
            // Attach JCEF console logger to help debug loading issues
            val log = Logger.getInstance(MyToolWindowFactory::class.java)
            browser?.let { attachConsoleLogger(it, log) }

            // Bridge for saving snapshots from the web page
            val br = browser!!
            try {
                saveQuery = createJsQueryCompat(br)
                Logging.info(project, MyToolWindowFactory::class.java, "saveQuery initialized successfully")
            } catch (e: Exception) {
                Logging.warn(project, MyToolWindowFactory::class.java, "Failed to initialize saveQuery - export bridge will not work", e)
                saveQuery = null
            }
            // Simple notify bridge: show a balloon/message from JS
            try {
                notifyQuery = createJsQueryCompat(br)
                Logging.info(project, MyToolWindowFactory::class.java, "notifyQuery initialized successfully")
            } catch (e: Exception) {
                Logging.warn(project, MyToolWindowFactory::class.java, "Failed to initialize notifyQuery - notifications may not work", e)
                notifyQuery = null
            }
            // Console bridge: forward webview console logs into IDE log
            try {
                consoleQuery = createJsQueryCompat(br)
                Logging.info(project, MyToolWindowFactory::class.java, "consoleQuery initialized successfully")
            } catch (e: Exception) {
                Logging.warn(project, MyToolWindowFactory::class.java, "Failed to initialize consoleQuery - console forwarding may not work", e)
                consoleQuery = null
            }
            notifyQuery?.addHandler { msg ->
                try {
                    val dirPath = try { settings.getSnapshotDirectoryPath() } catch (_: Exception) { null }
                    // Sanitize message to avoid leaking function source or huge content into IDE notifications
                    val safeMsg = run {
                        val raw = (msg ?: "").trim()
                        var t = raw.replace(Regex("\\s+"), " ")
                        val looksLikeCode = t.startsWith("function") || t.startsWith("class ") || (t.startsWith("(") && t.contains("=>")) || (t.contains("=>") && t.contains("{"))
                        if (looksLikeCode) {
                            t = "Notification from Bubble Units"
                        }
                        val max = 500
                        if (t.length > max) t.substring(0, max) + "…" else t
                    }
                    val body = buildString {
                        if (safeMsg.isNotBlank()) append(safeMsg)
                        if (!dirPath.isNullOrBlank()) {
                            if (isNotEmpty()) append('\n')
                            append("Snapshot directory: ")
                            append(dirPath)
                        }
                    }
                    com.intellij.notification.NotificationGroupManager.getInstance()
                        .getNotificationGroup("BubbleUnits")
                        .createNotification(
                            MyBundle.message("bubbleToolWindowTitle"),
                            body,
                            com.intellij.notification.NotificationType.INFORMATION
                        ).notify(project)
                } catch (_: Exception) {}
                JBCefJSQuery.Response("OK")
            }
            // Console bridge: forward console.* from the page into the IDE log
            consoleQuery?.addHandler { payload ->
                val raw = (payload ?: "").trim()
                val idx = raw.indexOf('\t')
                val level = if (idx > 0) raw.substring(0, idx) else "log"
                val msg = if (idx > 0) raw.substring(idx + 1) else raw
                when (level.lowercase()) {
                    "error" -> log.warn("[WebConsole][error] $msg")
                    "warn", "warning" -> log.warn("[WebConsole][warn] $msg")
                    else -> log.info("[WebConsole][$level] $msg")
                }
                JBCefJSQuery.Response("OK")
            }
            // Lightweight hash-based fallback bridge (works even if JSQuery is unavailable on some schemes)
            br.jbCefClient.addDisplayHandler(object: CefDisplayHandlerAdapter() {
                override fun onAddressChange(
                    browser: org.cef.browser.CefBrowser?,
                    frame: CefFrame?,
                    url: String?
                ) {
                    if (frame == null || !frame.isMain) return
                    val u = url ?: return
                    val hashIdx = u.indexOf('#')
                    if (hashIdx < 0) return
                    val hash = u.substring(hashIdx + 1)
                    try {
                        when {
                            hash.startsWith("__buNotify=") -> {
                                val enc = hash.substringAfter("__buNotify=").substringBefore('&')
                                val msg = try { java.net.URLDecoder.decode(enc, java.nio.charset.StandardCharsets.UTF_8) } catch (_: Exception) { enc }
                                try {
                                    com.intellij.notification.NotificationGroupManager.getInstance()
                                        .getNotificationGroup("BubbleUnits")
                                        .createNotification(
                                            MyBundle.message("bubbleToolWindowTitle"),
                                            msg,
                                            com.intellij.notification.NotificationType.INFORMATION
                                        ).notify(project)
                                } catch (_: Exception) {}
                                try { browser?.executeJavaScript("try{history.replaceState(null,'',location.pathname+location.search);}catch(e){}", u, 0) } catch (_: Exception) {}
                            }
                            hash.startsWith("__buSave=") -> {
                                val params = hash.substringAfter("__buSave=")
                                val namePart = params.substringBefore('&')
                                val dataPart = params.substringAfter("&d=", "")
                                val kindAndName = try { java.net.URLDecoder.decode(namePart, java.nio.charset.StandardCharsets.UTF_8) } catch (_: Exception) { namePart }
                                val sep = kindAndName.indexOf('|')
                                val kind = if (sep > 0) kindAndName.substring(0, sep) else "file"
                                val name = if (sep > 0) kindAndName.substring(sep + 1) else "snapshot"
                                try {
                                    val bytes = java.util.Base64.getDecoder().decode(dataPart)
                                    val dir = settings.getOrCreateSnapshotDir()
                                    // Add timestamp to filename to avoid overwriting
                                    val timestamp = java.time.LocalDateTime.now().format(
                                        java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
                                    )
                                    val baseName = name.substringBeforeLast(".")
                                    val extension = if (name.contains(".")) ".${name.substringAfterLast(".")}" else ""
                                    val timestampedName = "${baseName}-${timestamp}${extension}"
                                    val file = java.io.File(dir, timestampedName)
                                    file.writeBytes(bytes)
                                    // Refresh the VFS to ensure the snapshot folder shows the new file
                                    try {
                                        val vFile = com.intellij.openapi.vfs.LocalFileSystem.getInstance().refreshAndFindFileByIoFile(dir)
                                        if (vFile != null) {
                                            vFile.refresh(false, true)
                                        }
                                    } catch (_: Exception) {}
                                    try {
                                        addExport(kind, file.absolutePath)
                                    } catch (_: Exception) {}
                                } catch (e: Exception) {
                                    Logging.warn(project, MyToolWindowFactory::class.java, "Hash bridge save failed", e)
                                }
                                try { browser?.executeJavaScript("try{history.replaceState(null,'',location.pathname+location.search);}catch(e){}", u, 0) } catch (_: Exception) {}
                            }
                        }
                    } catch (_: Exception) {}
                }
            }, br.cefBrowser)
            // Accumulator for chunked transfers: id -> (kind, name, data)
            data class Pending(val kind: String, val name: String, val sb: StringBuilder)
            val pendingMap = java.util.concurrent.ConcurrentHashMap<String, Pending>()
            val maxAccumulatedLen = 32 * 1024 * 1024 // 32 MiB safety cap
            saveQuery?.addHandler { request ->
                try {
                    // Protocols supported:
                    // 1) Single-shot: "<kind>\t<name>\t<base64>"
                    // 2) Chunked:
                    //    BEGIN\t<id>\t<kind>\t<name>
                    //    CHUNK\t<id>\t<base64-chunk>
                    //    END\t<id>
                    val parts = request.split('\t')
                    val command = parts.firstOrNull()?.uppercase()
                    
                    return@addHandler when (command) {
                        "BEGIN" -> {
                            val id = parts.getOrNull(1)?.ifBlank { null }
                            val kind = parts.getOrNull(2)?.lowercase() ?: "file"
                            val name = parts.getOrNull(3)?.ifBlank { "snapshot" } ?: "snapshot"
                            if (id == null) {
                                JBCefJSQuery.Response(null, 400, "Missing id")
                            } else {
                                pendingMap[id] = Pending(kind, name, StringBuilder())
                                JBCefJSQuery.Response("OK")
                            }
                        }
                        "CHUNK" -> {
                            val id = parts.getOrNull(1)
                            val data = parts.getOrNull(2) ?: ""
                            val p = if (id != null) pendingMap[id] else null
                            if (p == null) {
                                JBCefJSQuery.Response(null, 404, "No session")
                            } else {
                                if (p.sb.length + data.length > maxAccumulatedLen) {
                                    pendingMap.remove(id)
                                    JBCefJSQuery.Response(null, 413, "Too large")
                                } else {
                                    p.sb.append(data)
                                    JBCefJSQuery.Response("OK")
                                }
                            }
                        }
                        "END" -> {
                            val id = parts.getOrNull(1)
                            val p = if (id != null) pendingMap.remove(id) else null
                            if (p == null) {
                                JBCefJSQuery.Response(null, 404, "No session")
                            } else {
                                val dataStr = p.sb.toString()
                                if (dataStr.isEmpty()) {
                                    JBCefJSQuery.Response(null, 400, "Empty payload")
                                } else {
                                    val bytes = java.util.Base64.getDecoder().decode(dataStr)
                                    val dir = settings.getOrCreateSnapshotDir()
                                    // Add timestamp to filename to avoid overwriting
                                    val timestamp = java.time.LocalDateTime.now().format(
                                        java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
                                    )
                                    val baseName = p.name.substringBeforeLast(".")
                                    val extension = if (p.name.contains(".")) ".${p.name.substringAfterLast(".")}" else ""
                                    val timestampedName = "${baseName}-${timestamp}${extension}"
                                    val file = java.io.File(dir, timestampedName)
                                    file.writeBytes(bytes)
                                    // Refresh the VFS to ensure the snapshot folder shows the new file
                                    try {
                                        val vFile = com.intellij.openapi.vfs.LocalFileSystem.getInstance().refreshAndFindFileByIoFile(dir)
                                        if (vFile != null) {
                                            vFile.refresh(false, true)
                                        }
                                    } catch (_: Exception) {}
                                    try {
                                        addExport(p.kind, file.absolutePath)
                                    } catch (_: Exception) {}
                                    JBCefJSQuery.Response("OK")
                                }
                            }
                        }
                        else -> {
                            // Single-shot fallback
                            val kind = parts.getOrNull(0)?.lowercase() ?: "file"
                            val name = parts.getOrNull(1)?.ifBlank { "snapshot" } ?: "snapshot"
                            val b64 = parts.drop(2).joinToString("\t") // in case the payload contained tabs
                            if (b64.isBlank()) {
                                JBCefJSQuery.Response(null, 400, "Empty payload")
                            } else {
                                val bytes = java.util.Base64.getDecoder().decode(b64)
                                val dir = settings.getOrCreateSnapshotDir()
                                // Add timestamp to filename to avoid overwriting
                                val timestamp = java.time.LocalDateTime.now().format(
                                    java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
                                )
                                val baseName = name.substringBeforeLast(".")
                                val extension = if (name.contains(".")) ".${name.substringAfterLast(".")}" else ""
                                val timestampedName = "${baseName}-${timestamp}${extension}"
                                val file = java.io.File(dir, timestampedName)
                                file.writeBytes(bytes)
                                // Refresh the VFS to ensure the snapshot folder shows the new file
                                try {
                                    val vFile = com.intellij.openapi.vfs.LocalFileSystem.getInstance().refreshAndFindFileByIoFile(dir)
                                    if (vFile != null) {
                                        vFile.refresh(false, true)
                                    }
                                } catch (_: Exception) {}
                                try {
                                    addExport(kind, file.absolutePath)
                                } catch (_: Exception) {}
                                JBCefJSQuery.Response("OK")
                            }
                        }
                    }
                } catch (e: Exception) {
                    Logging.warn(project, MyToolWindowFactory::class.java, "Save snapshot failed", e)
                    JBCefJSQuery.Response(null, 500, e.message)
                }
            }

            br.jbCefClient.addLoadHandler(object: CefLoadHandlerAdapter() {
                override fun onLoadStart(browser: org.cef.browser.CefBrowser?, frame: CefFrame?, transitionType: org.cef.network.CefRequest.TransitionType?) {
                    if (frame == null || !frame.isMain) return
                    // DO NOT create early stubs - let JSQuery.inject() be the first to define these properties
                    // Early stubs were preventing inject() from properly defining the real bridges
                }
                override fun onLoadEnd(browser: org.cef.browser.CefBrowser?, frame: CefFrame?, httpStatusCode: Int) {
                    if (frame == null || !frame.isMain) return
                    try {
                        // Log warnings if queries are null during initial bridge injection
                        if (saveQuery == null) {
                            Logging.warn(project, MyToolWindowFactory::class.java, "saveQuery is null in onLoadEnd - export bridge cannot be injected")
                        }
                        if (notifyQuery == null) {
                            Logging.warn(project, MyToolWindowFactory::class.java, "notifyQuery is null in onLoadEnd - notification bridge cannot be injected")
                        }
                        if (consoleQuery == null) {
                            Logging.warn(project, MyToolWindowFactory::class.java, "consoleQuery is null in onLoadEnd - console bridge cannot be injected")
                        }

                        // Execute inject() code to define the bridge functions
                        // This should replace our stubs with the real implementations
                        val injectFn = (saveQuery?.inject("__bubbleSave_send") ?: "") + ";\n" + (notifyQuery?.inject("__bubbleNotify_bridge") ?: "") + ";\n" + (consoleQuery?.inject("__bubbleConsole_bridge") ?: "") + ";\n"
                        val injectPreview = injectFn.take(200)
                        Logging.info(project, MyToolWindowFactory::class.java, "JSQuery.inject() generated code (first 200 chars): $injectPreview")

                        // Execute inject code after stubs
                        browser?.executeJavaScript(injectFn, frame.url, 0)
                        
                        // Add fallback for notification bridge if it failed to inject
                        val fallbackJs = """
                            // Fallback for notification bridge if it failed to inject
                            if (typeof window.__bubbleNotify_bridge !== 'function') {
                                console.info('[BubbleUnits] Creating fallback for __bubbleNotify_bridge');
                                window.__bubbleNotify_bridge = function(msg) {
                                    console.info('[BubbleUnits] Fallback notification: ' + msg);
                                    try {
                                        location.hash = '#__buNotify=' + encodeURIComponent(String(msg||'')) + '&t=' + Date.now();
                                        return true;
                                    } catch (e) {
                                        console.error('[BubbleUnits] Fallback notification failed:', e);
                                        return false;
                                    }
                                };
                            }
                        """.trimIndent()
                        browser?.executeJavaScript(fallbackJs, frame.url, 0)

                        // Verify bridges were injected
                        val verifyBridges = """
                            try {
                              console.info('[BubbleUnits] Bridge verification:');
                              console.info('  __bubbleSave_send: ' + typeof window.__bubbleSave_send);
                              console.info('  __bubbleNotify_bridge: ' + typeof window.__bubbleNotify_bridge);
                              console.info('  __bubbleConsole_bridge: ' + typeof window.__bubbleConsole_bridge);
                            } catch (e) { console.error('[BubbleUnits] Failed to verify bridges:', e); }
                        """.trimIndent()
                        browser?.executeJavaScript(verifyBridges, frame.url, 0)
                        val js = """
                            // Define fallback for __bubbleSave_send if injection failed
                            if (typeof window.__bubbleSave_send !== 'function') {
                              console.info('[BubbleUnits] Creating fallback for __bubbleSave_send');
                              window.__bubbleSave_send = function(data) {
                                console.info('[BubbleUnits] __bubbleSave_send fallback called with data length: ' + (data ? data.length : 0));
                                // Use hash-based fallback mechanism
                                try {
                                  if (!data) return;
                                  var parts = data.split('\t');
                                  var command = parts[0];
                                  
                                  // For BEGIN command
                                  if (command === 'BEGIN') {
                                    var id = parts[1] || '';
                                    var kind = parts[2] || 'file';
                                    var name = parts[3] || 'snapshot';
                                    window.__bubbleExportSession = { id: id, kind: kind, name: name, chunks: [] };
                                    return;
                                  }
                                  
                                  // For CHUNK command
                                  if (command === 'CHUNK') {
                                    if (!window.__bubbleExportSession) return;
                                    var chunk = parts[2] || '';
                                    window.__bubbleExportSession.chunks.push(chunk);
                                    return;
                                  }
                                  
                                  // For END command
                                  if (command === 'END') {
                                    if (!window.__bubbleExportSession) return;
                                    var session = window.__bubbleExportSession;
                                    var b64 = session.chunks.join('');
                                    var url = '#__buSave=' + encodeURIComponent(session.kind + '|' + session.name) + '&d=' + b64;
                                    window.location.href = url;
                                    window.__bubbleExportSession = null;
                                    return;
                                  }
                                  
                                  // For single-shot export
                                  var kind = parts[0] || 'file';
                                  var name = parts[1] || 'snapshot';
                                  var b64 = parts.slice(2).join('\t');
                                  var url = '#__buSave=' + encodeURIComponent(kind + '|' + name) + '&d=' + b64;
                                  window.location.href = url;
                                } catch (e) {
                                  console.error('[BubbleUnits] Fallback __bubbleSave_send error:', e);
                                }
                              };
                            }
                            
                            // Define a unified notify that forwards to the bridge function, looking it up dynamically each time
                            try {
                              (function(){
                                window.__bubbleNotify = function(msg){
                                  try {
                                    // Look up the bridge dynamically to ensure we get the real JSQuery bridge, not the early stub
                                    var bridge = (typeof window !== 'undefined' && typeof window.__bubbleNotify_bridge === 'function') ? window.__bubbleNotify_bridge : null;
                                    if (bridge) { bridge(String(msg == null ? '' : msg)); }
                                    else { console.info('[BubbleUnits] notify: bridge not ready - ' + (msg || '')); }
                                  } catch (e) { try { console.error(e); } catch(ex){} }
                                };
                              })();
                            } catch (e) {}
                            // Mirror console.* into the IDE log via console bridge, preserving original behavior
                            try {
                              (function(){
                                var bridge = (typeof window !== 'undefined' && typeof window.__bubbleConsole_bridge === 'function') ? window.__bubbleConsole_bridge : null;
                                if (!window.__bubbleConsoleHooked) {
                                  window.__bubbleConsoleHooked = true;
                                  var orig = { log: console.log, info: console.info, warn: console.warn, error: console.error, debug: console.debug };
                                  function asString(v){ try{ if(v==null) return String(v); if(typeof v==='string') return v; if(typeof v==='object') return JSON.stringify(v); return String(v);}catch(e){ try{return String(v);}catch(e2){return '[unprintable]';} } }
                                  function fwd(level, args){ try{ if(!bridge) return; var msg = Array.prototype.map.call(args, asString).join(' '); bridge(level+'\t'+msg); } catch(e){} }
                                  ['log','info','warn','error','debug'].forEach(function(k){ try{ console[k] = function(){ try{ orig[k] && orig[k].apply(console, arguments); }catch(e){}; fwd(k, arguments); }; } catch(e){} });
                                }
                              })();
                            } catch (e) {}
                            try { console.info('[BubbleUnits] Bridge injected (save, notify, console)'); } catch(e) {}
                            try { if (typeof window !== 'undefined' && typeof window.__bubbleNotify === 'function') { window.__bubbleNotify('BubbleUnits bridge ready'); } } catch(e) {}
                            (function(){
                              // Provide a sender that dynamically looks up the bridge to avoid hitting early stubs
                              function __bubbleSendRaw(s){
                                try {
                                  // Look up the bridge dynamically to ensure we get the real JSQuery bridge, not the early stub
                                  var bridge = (typeof window !== 'undefined' && typeof window.__bubbleSave_send === 'function') ? window.__bubbleSave_send : null;
                                  if (bridge) { bridge(s); return true; }
                                } catch(e) { console.error(e); }
                                return false;
                              }
                              // Wrapper that chunks payloads to avoid JCEF query size limits
                              window.__bubbleSave = (function(){
                                var MAX = 24000; // conservative chunk size
                                function sendRaw(s){ try{ if(!__bubbleSendRaw(s)){ console.info('[BubbleUnits] save bridge not ready'); try{ if (typeof window !== 'undefined' && typeof window.__bubbleNotify === 'function') { window.__bubbleNotify('Export bridge is not ready — try Reload (toolbar)'); } } catch(e2){} } }catch(e){ console.error(e);} }
                                function sendChunked(kind,name,b64){
                                  try{
                                    var id = Math.random().toString(36).slice(2);
                                    sendRaw('BEGIN\t'+id+'\t'+kind+'\t'+name);
                                    for(var i=0;i<b64.length;i+=MAX){
                                      var part = b64.slice(i, i+MAX);
                                      sendRaw('CHUNK\t'+id+'\t'+part);
                                    }
                                    sendRaw('END\t'+id);
                                  }catch(e){ console.error('chunked-save failed', e); }
                                }
                                return function(payload){
                                  try{
                                    if(typeof payload !== 'string') return;
                                    if(payload.length <= MAX){ sendRaw(payload); return; }
                                    var first = payload.indexOf('\t'); if(first<0) return;
                                    var second = payload.indexOf('\t', first+1); if(second<0) return;
                                    var kind = payload.slice(0, first);
                                    var name = payload.slice(first+1, second);
                                    var b64 = payload.slice(second+1);
                                    if(b64.length <= MAX){ sendRaw(payload); return; }
                                    sendChunked(kind, name, b64);
                                  }catch(e){ console.error(e); }
                                }
                              })();
                              
                              // Create the __bubble object expected by bubbles.js
                              // Note: This is replaced by a more comprehensive implementation below
                              function ts(){ var d=new Date(); return d.toISOString().replace(/[:.]/g,'-'); }
                              function serializeSvg(){
                                var el = document.querySelector('#bubbles svg') || document.querySelector('svg');
                                if(!el) return null;
                                var copy = el.cloneNode(true);
                                try {
                                  var r = getComputedStyle(document.documentElement);
                                  var css = '.bubbles circle.success{fill:'+r.getPropertyValue('--color-success').trim()+';}' +
                                            '.bubbles circle.failed{fill:'+r.getPropertyValue('--color-failed').trim()+';}' +
                                            '.bubbles circle.error{fill:'+r.getPropertyValue('--color-error').trim()+';}' +
                                            '.bubbles circle.skipped{fill:'+r.getPropertyValue('--color-warning').trim()+';}';
                                  var styleEl=document.createElement('style'); styleEl.textContent=css; copy.insertBefore(styleEl, copy.firstChild);
                                } catch(e){}
                                var s = new XMLSerializer().serializeToString(copy);
                                if(!/^<svg[\s\S]*xmlns=/.test(s)) s = s.replace('<svg','<svg xmlns=\"http://www.w3.org/2000/svg\"');
                                return s;
                              }
                              function toPng(svgText){
                                return new Promise(function(resolve,reject){
                                  try{
                                    var img = new Image();
                                    img.onload = function(){
                                      var c=document.createElement('canvas'); c.width=img.width; c.height=img.height;
                                      var ctx=c.getContext('2d'); ctx.drawImage(img,0,0);
                                      resolve(c.toDataURL('image/png').split(',')[1]);
                                    };
                                    img.onerror=reject;
                                    img.src='data:image/svg+xml;charset=utf-8,'+encodeURIComponent(svgText);
                                  } catch(e){ reject(e); }
                                });
                              }
                              function buNotify(msg){
                                try { if (typeof window.__bubbleNotify==='function') { window.__bubbleNotify(msg); return; } } catch(e){}
                                try { location.hash = '#__buNotify=' + encodeURIComponent(String(msg||'')) + '&t=' + Date.now(); } catch(e){}
                              }
                              function buSend(kind,name,b64){
                                try { if (typeof window.__bubbleSave==='function') { window.__bubbleSave(kind+'\t'+name+'\t'+b64); return; } } catch(e) {}
                                try { location.hash = '#__buSave=' + encodeURIComponent(kind+'|'+name) + '&d=' + b64; } catch(e){}
                              }
                              // Provide a stable window.__bubble API used by bubbles.js
                              try {
                                window.__bubble = {};
                                // Core save function that handles both images and other content
                                window.__bubble.save = function(kind, name, b64){
                                  try { buSend(String(kind||'file'), String(name||'snapshot'), String(b64||'')); return Promise.resolve('OK'); } catch(e){ return Promise.reject(e); }
                                };
                                // Export helpers used by bubbles.js
                                window.__bubble.exportSvg = function(name, b64){ return window.__bubble.save('svg', name, b64); };
                                window.__bubble.exportJson = function(name, b64){ return window.__bubble.save('json', name, b64); };
                                window.__bubble.exportPng = function(name, b64){ return window.__bubble.save('png', name, b64); };
                                // Add a notify function to ensure notifications work through the bubbles.js API
                                window.__bubble.notify = function(msg) {
                                  try { 
                                    if (typeof window.__bubbleNotify === 'function') {
                                      window.__bubbleNotify(msg);
                                      return Promise.resolve('OK');
                                    } else {
                                      buNotify(msg);
                                      return Promise.resolve('OK (fallback)');
                                    }
                                  } catch(e){ 
                                    console.error('[BubbleUnits] notify failed:', e);
                                    return Promise.reject(e); 
                                  }
                                };
                                // Log that the API is ready
                                console.info('[BubbleUnits] window.__bubble API initialized successfully');
                              } catch(e) { try { console.error('[BubbleUnits] Failed to initialize window.__bubble API:', e); } catch(ex) {} }
                              function bindIfPresent(el, listener) {
                                try {
                                  if (!el) return;
                                  var m = el.getAttribute('data-bu-bound');
                                  if (m === '1') return;
                                  el.setAttribute('data-bu-bound','1');
                                  el.addEventListener('click', listener, true);
                                } catch(e) { try { console.error(e); } catch(ex) {} }
                              }
                              function tryBindButtons(){
                                try {
                                  var svgBtn = document.getElementById('svg_download_link');
                                  var pngBtn = document.getElementById('png_download_link');
                                  var jsonBtn = document.getElementById('json_report_download_link');
                                  bindIfPresent(svgBtn, function(ev){
                                    ev.preventDefault();
                                    try { if (typeof window.__bubbleNotify === 'function') { window.__bubbleNotify('Exporting SVG…'); } } catch(e) {}
                                    var s = serializeSvg();
                                    if (!s) {
                                      try { if (typeof window.__bubbleNotify === 'function') { window.__bubbleNotify('No SVG found to export yet. Generate the chart first.'); } } catch(e) {}
                                      return;
                                    }
                                    var b = btoa(unescape(encodeURIComponent(s)));
                                    try { if (typeof window.__bubbleNotify === 'function') { window.__bubbleNotify('Saving: phpunit-bubble-report.svg'); } } catch(e) {}
                                    buSend('svg','phpunit-bubble-report.svg', b);
                                  });
                                  bindIfPresent(pngBtn, async function(ev){
                                    ev.preventDefault();
                                    var s = serializeSvg();
                                    if (!s) {
                                      try { if (typeof window.__bubbleNotify === 'function') { window.__bubbleNotify('No SVG found to export yet. Generate the chart first.'); } } catch(e) {}
                                      return;
                                    }
                                    try { if (typeof window.__bubbleNotify === 'function') { window.__bubbleNotify('Exporting PNG…'); } } catch(e) {}
                                    var b = await toPng(s);
                                    try { if (typeof window.__bubbleNotify === 'function') { window.__bubbleNotify('Saving: phpunit-bubble-report.png'); } } catch(e) {}
                                    buSend('png','phpunit-bubble-report.png', b);
                                  });
                                  bindIfPresent(jsonBtn, function(ev){
                                    ev.preventDefault();
                                    var data = {circles: Array.from(document.querySelectorAll('#bubbles svg circle')).map(function(c){ return { cx:c.getAttribute('cx'), cy:c.getAttribute('cy'), r:c.getAttribute('r'), cls:c.getAttribute('class') }; })};
                                    var txt = JSON.stringify(data, null, 2);
                                    var b = btoa(unescape(encodeURIComponent(txt)));
                                    try { if (typeof window.__bubbleNotify === 'function') { window.__bubbleNotify('Saving: phpunit-bubble-report.json'); } } catch(e) {}
                                    buSend('json','phpunit-bubble-report.json', b);
                                  });
                                } catch(e) { try { console.error(e); } catch(ex) {} }
                              }
                              function onReady(){
                                tryBindButtons();
                                try {
                                  if (window.__buButtonsObserver) return;
                                  var obs = new MutationObserver(function(){ tryBindButtons(); });
                                  window.__buButtonsObserver = obs;
                                  var root = document.body || document.documentElement;
                                  if (root) obs.observe(root, {childList:true, subtree:true});
                                } catch(e) { try { console.error(e); } catch(ex) {} }
                              }
                              if (document.readyState === 'complete' || document.readyState === 'interactive') onReady(); else document.addEventListener('DOMContentLoaded', onReady);
                            })();
                        """.trimIndent()
                        browser?.executeJavaScript(js, frame.url, 0)
                        val delegateJs = """
                            (function(){
                              try {
                                if (!window.__buDelegated) {
                                  window.__buDelegated = true;
                                  function __buSerializeSvg(){
                                    try {
                                      var el = document.querySelector('#bubbles svg') || document.querySelector('svg');
                                      if(!el) return null;
                                      var copy = el.cloneNode(true);
                                      try {
                                        var r = getComputedStyle(document.documentElement);
                                        var css = '.bubbles circle.success{fill:'+r.getPropertyValue('--color-success').trim()+';}' +
                                                  '.bubbles circle.failed{fill:'+r.getPropertyValue('--color-failed').trim()+';}' +
                                                  '.bubbles circle.error{fill:'+r.getPropertyValue('--color-error').trim()+';}' +
                                                  '.bubbles circle.skipped{fill:'+r.getPropertyValue('--color-warning').trim()+';}';
                                        var styleEl=document.createElement('style'); styleEl.textContent=css; copy.insertBefore(styleEl, copy.firstChild);
                                      } catch(e){}
                                      var s = new XMLSerializer().serializeToString(copy);
                                      if(!/^<svg[\s\S]*xmlns=/.test(s)) s = s.replace('<svg','<svg xmlns=\"http://www.w3.org/2000/svg\"');
                                      return s;
                                    } catch(e){ return null; }
                                  }
                                  function __buToPng(svgText){ return new Promise(function(resolve,reject){ try{ var img=new Image(); img.onload=function(){ var c=document.createElement('canvas'); c.width=img.width; c.height=img.height; var ctx=c.getContext('2d'); ctx.drawImage(img,0,0); resolve(c.toDataURL('image/png').split(',')[1]); }; img.onerror=reject; img.src='data:image/svg+xml;charset=utf-8,'+encodeURIComponent(svgText);}catch(e){reject(e);} }); }
                                  function __buSend(kind,name,b64){
                                    try {
                                      if (typeof window.__bubbleSave==='function') {
                                        window.__bubbleSave(kind+'\t'+name+'\t'+b64);
                                      }
                                    } catch(e) { console.error(e); }
                                  }
                                  document.addEventListener('click', function(ev){
                                    try {
                                      var t = ev.target && ev.target.closest ? ev.target.closest('#svg_download_link, #png_download_link, #json_report_download_link') : null;
                                      if (!t) return;
                                      ev.preventDefault();
                                      var id = (t.id||'').toLowerCase();
                                      if (id === 'svg_download_link') {
                                        var s = __buSerializeSvg(); if(!s){ try{ window.__bubbleNotify && window.__bubbleNotify('No SVG found to export'); }catch(e){}; return; }
                                        var b = btoa(unescape(encodeURIComponent(s)));
                                        __buSend('svg','phpunit-bubble-report.svg', b);
                                      } else if (id === 'png_download_link') {
                                        var s2 = __buSerializeSvg(); if(!s2){ try{ window.__bubbleNotify && window.__bubbleNotify('No SVG found to export'); }catch(e){}; return; }
                                        __buToPng(s2).then(function(b){ __buSend('png','phpunit-bubble-report.png', b); }).catch(function(){});
                                      } else if (id === 'json_report_download_link') {
                                        var data = {circles: Array.from(document.querySelectorAll('#bubbles svg circle')).map(function(c){ return { cx:c.getAttribute('cx'), cy:c.getAttribute('cy'), r:c.getAttribute('r'), cls:c.getAttribute('class') }; })};
                                        var txt = JSON.stringify(data, null, 2);
                                        var b3 = btoa(unescape(encodeURIComponent(txt)));
                                        __buSend('json','phpunit-bubble-report.json', b3);
                                      }
                                    } catch(e) { try { console.error(e); } catch(ex) {} }
                                  }, true);
                                }
                              } catch(e) {}
                            })();
                        """.trimIndent()
                        browser?.executeJavaScript(delegateJs, frame.url, 0)
                    } catch (_: Exception) {}
                }
            }, br.cefBrowser)

            contentPanel.add(browser!!.component, BorderLayout.CENTER)
            contentPanel.revalidate()
            contentPanel.repaint()

            // Load persisted last URL first (if available) for fast startup, then refresh
            val persisted = settings.getState().lastLoadedUrl
            if (!persisted.isNullOrBlank()) {
                browser?.loadURL(persisted)
            }
            loadUrl()
        }

        private fun disposeBrowser() {
            browser?.let {
                contentPanel.remove(it.component)
                try { saveQuery?.dispose() } catch (_: Exception) {}
                try { notifyQuery?.dispose() } catch (_: Exception) {}
                try { consoleQuery?.dispose() } catch (_: Exception) {}
                saveQuery = null
                notifyQuery = null
                consoleQuery = null
                it.dispose()
                browser = null
                contentPanel.revalidate()
                contentPanel.repaint()
            }
        }

        private fun loadUrl() {
            // Resolve URL off the EDT to avoid blocking UI; then load on EDT
            val app = com.intellij.openapi.application.ApplicationManager.getApplication()
            val loadStartNs = System.nanoTime()
            app.executeOnPooledThread {
                val targetUrl = try {
                    settings.url
                } catch (e: Exception) {
                    Logging.warn(project, MyToolWindowFactory::class.java, "Failed to resolve URL", e)
                    // Surface in Event Log to aid troubleshooting
                    try {
                        val notifier = com.intellij.notification.NotificationGroupManager.getInstance()
                            .getNotificationGroup("BubbleUnits")
                        notifier.createNotification(
                            MyBundle.message("bubbleToolWindowTitle"),
                            MyBundle.message("bubbleToolWindow.loadError"),
                            com.intellij.notification.NotificationType.ERROR
                        ).notify(project)
                    } catch (_: Exception) {
                        // ignore notification issues
                    }
                    // Fallback: keep previous or a minimal error page
                    "data:text/html,<html><body><p>" + MyBundle.message("bubbleToolWindow.loadError") + "</p></body></html>"
                }
                // Persist last loaded URL for fast startup across IDE restarts
                try {
                    settings.setLastLoadedUrl(targetUrl)
                } catch (_: Exception) {
                    // ignore persistence errors
                }
                app.invokeLater({
                    val br = browser
                    if (br == null) return@invokeLater
                    val isDataHtml = targetUrl.startsWith("data:text/html")
                    val resolveMs = (System.nanoTime() - loadStartNs) / 1_000_000
                    Logging.debug(
                        project,
                        MyToolWindowFactory::class.java,
                        "loadUrl: resolved in ${resolveMs} ms (isDataHtml=${isDataHtml})"
                    )
                    if (isDataHtml) {
                        // Robust path: decode data URL and load as a real HTML document with a stable base URL.
                        // This ensures the JBCef JSQuery bridge is available and export buttons work reliably.
                        try {
                            val comma = targetUrl.indexOf(',')
                            val meta = if (comma >= 0) targetUrl.substring(0, comma) else targetUrl
                            val payload = if (comma >= 0) targetUrl.substring(comma + 1) else ""
                            val isB64 = meta.contains(";base64", ignoreCase = true)
                            if (isB64 && payload.isNotEmpty()) {
                                val bytes = java.util.Base64.getDecoder().decode(payload)
                                val html = String(bytes, java.nio.charset.StandardCharsets.UTF_8)
                                // Use a fake http base URL so relative paths and the JS bridge work as expected.
                                br.loadHTML(html, "http://bubbleunits.local/bubble.html")
                            } else {
                                // Non-base64 data URL: fall back to direct load
                                br.loadURL(targetUrl)
                            }
                        } catch (ex: Exception) {
                            Logging.warn(
                                project,
                                MyToolWindowFactory::class.java,
                                "Decoding data HTML failed; falling back to direct URL",
                                ex
                            )
                            br.loadURL(targetUrl)
                        }
                    } else {
                        br.loadURL(targetUrl)
                    }
                }, com.intellij.openapi.application.ModalityState.any())
            }
        }
    }
}
