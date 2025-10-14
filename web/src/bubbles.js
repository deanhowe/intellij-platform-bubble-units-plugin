import {json, select} from 'd3';
import {ReportTransformer} from './report-transformer';
import {phpUnitBubbles} from './php-unit-bubbles';

function getClassStyles(className, filter = false, allowed = []) {
    /*
        The intention was to use this to crate the styles for the exported SVG, but it was/is too verbose…
        It's still here because I might come back to it…
     */
    const dummyElement = document.createElement('div');
    dummyElement.className = className;
    document.body.appendChild(dummyElement);

    const filterFn = (property) => property.startsWith('--') || property.includes('color');

    const styles = getComputedStyle(dummyElement);
    const cssText = Array.from(styles).reduce((css, property) => {

        if (filter && !allowed.includes(property)) {
            return css;
        }
        if (filter && filterFn(property)) {
            return css;
        }

        return `${css}${property}:${styles.getPropertyValue(property)};`;
    }, '');

    document.body.removeChild(dummyElement);
    return cssText;
}

function cloneSVG(svgSelector = 'svg.bubbles') {
    try {
        const svgElement = document.querySelector(svgSelector) || document.querySelector('svg');
        if (!svgElement) {
            console.error('[BubbleUnits] cloneSVG: no SVG element found');
            return null;
        }
        const copy = svgElement.cloneNode(true);
        // Inline minimal CSS using theme variables so exported SVG keeps colors
        try {
            const r = getComputedStyle(document.documentElement);
            // Get actual color values or use fallbacks
            const successColor = r.getPropertyValue('--color-success').trim() || '#2e7d32';
            const failedColor = r.getPropertyValue('--color-failed').trim() || '#c62828';
            const errorColor = r.getPropertyValue('--color-error').trim() || '#d32f2f';
            const warningColor = r.getPropertyValue('--color-warning').trim() || '#f57c00';
            
            console.info('[BubbleUnits] Using colors for export:', {
                success: successColor,
                failed: failedColor,
                error: errorColor,
                warning: warningColor
            });
            
//             const css = `.bubbles circle{stroke:#aaa;stroke-width:1px;}
// .bubbles circle.success{fill:${successColor};}
// .bubbles circle.failed{fill:${failedColor};}
// .bubbles circle.error{fill:${errorColor};}
// .bubbles circle.skipped{fill:${warningColor};}`;
//             const styleEl = document.createElement('style');
//             styleEl.textContent = css;
//             copy.insertBefore(styleEl, copy.firstChild);
            
            // Also directly set fill colors on circles to ensure they're visible
            copy.querySelectorAll('circle.success').forEach(c => c.setAttribute('fill', successColor));
            copy.querySelectorAll('circle.failed').forEach(c => c.setAttribute('fill', failedColor));
            copy.querySelectorAll('circle.error').forEach(c => c.setAttribute('fill', errorColor));
            copy.querySelectorAll('circle.skipped').forEach(c => c.setAttribute('fill', warningColor));
        } catch (e) {
            console.warn('[BubbleUnits] cloneSVG: failed to inline CSS', e);
        }
        let s = new XMLSerializer().serializeToString(copy);
        if (!/^<svg[\s\S]*xmlns=/.test(s)) s = s.replace('<svg', '<svg xmlns="http://www.w3.org/2000/svg"');
        return s;
    } catch (e) {
        console.error('[BubbleUnits] cloneSVG failed', e);
        return null;
    }
}

function cloneJsonDownload(jsonReport, downloadLinkId) {
    try {
        const jsonText = JSON.stringify(jsonReport, null, 2);
        const b64 = base64FromText(jsonText);
        const dataUrl = 'data:application/json;charset=utf-8;base64,' + b64;
        const downloadLink = document.getElementById(downloadLinkId);
        if (downloadLink) {
            downloadLink.href = dataUrl;
            downloadLink.download = 'phpunit-bubble-report.json';
        }
        console.info('[BubbleUnits] JSON export ready (data URL, length=' + b64.length + ')');
    } catch (e) { console.error('[BubbleUnits] cloneJsonDownload failed', e); }
}

function cloneSVGToDownload(svgSelector = 'svg.bubbles', downloadLinkId) {
    try {
        const svgData = cloneSVG(svgSelector);
        if (!svgData) return;
        const b64 = base64FromText(svgData);
        const dataUrl = 'data:image/svg+xml;charset=utf-8;base64,' + b64;
        const downloadLink = document.getElementById(downloadLinkId);
        if (downloadLink) {
            downloadLink.href = dataUrl;
            downloadLink.download = 'phpunit-bubble-report.svg';
        }
        console.info('[BubbleUnits] SVG export ready (data URL, length=' + b64.length + ')');
    } catch (e) { console.error('[BubbleUnits] cloneSVGToDownload failed', e); }
}

function cloneSVGToPNGDownload(svgSelector = 'svg.bubbles', downloadLinkId) {
    try {
        const svgData = cloneSVG(svgSelector);
        if (!svgData) return;
        const img = new Image();
        img.onload = function () {
            const canvas = document.createElement('canvas');
            canvas.width = img.width || 800;
            canvas.height = img.height || 600;
            const ctx = canvas.getContext('2d');
            ctx.drawImage(img, 0, 0);
            try {
                const dataUrl = canvas.toDataURL('image/png');
                const downloadLink = document.getElementById(downloadLinkId);
                if (downloadLink) {
                    downloadLink.href = dataUrl;
                    downloadLink.download = 'phpunit-bubble-report.png';
                }
                // Log base64 length (strip prefix)
                const b64len = (dataUrl.split(',')[1] || '').length;
                console.info('[BubbleUnits] PNG export ready (data URL, length=' + b64len + ')');
            } catch (e) { console.error('[BubbleUnits] canvas toDataURL failed', e); }
        };
        img.onerror = function (e) { console.error('[BubbleUnits] PNG image load failed', e); };
        img.src = 'data:image/svg+xml;charset=utf-8,' + encodeURIComponent(svgData);
    } catch (e) { console.error('[BubbleUnits] cloneSVGToPNGDownload failed', e); }
}

function base64FromText(s) {
    try { return btoa(unescape(encodeURIComponent(s))); } catch (e) { console.error('[BubbleUnits] base64FromText failed', e); return ''; }
}
// function saveViaBridge(kind, name, base64) {
//     if (typeof window.__bubbleSave === 'function') {
//         const payload = kind + '\t' + name + '\t' + base64;
//         try { window.__bubbleSave(payload); console.info('[BubbleUnits] requested save via bridge:', kind, name, 'len', base64.length); } catch (e) { console.error('[BubbleUnits] __bubbleSave failed', e); }
//         return true;
//     }
//     return false;
// }
function saveViaBridge(kind, name, base64) {
    if (typeof window.__bubble === 'object') {
        try {
            let exportFn;
            switch (kind.toLowerCase()) {
                case 'svg':
                    exportFn = window.__bubble.exportSvg;
                    break;
                case 'json':
                    exportFn = window.__bubble.exportJson;
                    break;
                case 'png':
                    exportFn = window.__bubble.exportPng;
                    break;
                default:
                    console.error('[BubbleUnits] Unknown export type:', kind);
                    return false;
            }

            if (typeof exportFn === 'function') {
                exportFn(name, base64)
                    .then(() => {
                        console.info('[BubbleUnits] Export successful:', kind, name);
                        // Use the new notify function if available
                        if (typeof window.__bubble.notify === 'function') {
                            window.__bubble.notify('Saved ' + name + ' successfully');
                        } else if (typeof window.__bubbleNotify === 'function') {
                            window.__bubbleNotify('Saved ' + name + ' successfully');
                        }
                    })
                    .catch(err => console.error('[BubbleUnits] Export failed:', err));
                return true;
            }
        } catch (e) {
            console.error('[BubbleUnits] Bridge export failed:', e);
        }
    }
    console.error('[BubbleUnits] Bridge not available');
    return false;
}

// Helper function to send notifications through any available channel
function notifyViaBridge(message) {
    try {
        // Try the new __bubble.notify function first
        if (typeof window.__bubble === 'object' && typeof window.__bubble.notify === 'function') {
            window.__bubble.notify(message);
            return true;
        }
        // Fall back to the original __bubbleNotify function
        else if (typeof window.__bubbleNotify === 'function') {
            window.__bubbleNotify(message);
            return true;
        }
    } catch (e) {
        console.error('[BubbleUnits] Notification failed:', e);
    }
    return false;
}

async function toPngBase64(svgSelector = 'svg.bubbles') {
    return new Promise((resolve, reject) => {
        const s = cloneSVG(svgSelector);
        if (!s) { reject(new Error('No SVG')); return; }
        const canvas = document.createElement('canvas');
        const ctx = canvas.getContext('2d');
        const img = new Image();
        const url = URL.createObjectURL(new Blob([s], {type: 'image/svg+xml;charset=utf-8'}));
        img.onload = function () {
            canvas.width = img.width || 800;
            canvas.height = img.height || 600;
            ctx.drawImage(img, 0, 0);
            URL.revokeObjectURL(url);
            try { resolve(canvas.toDataURL('image/png').split(',')[1]); } catch (e) { reject(e); }
        };
        img.onerror = reject;
        img.src = url;
    });
}

function setupSnapshotExports(jsonReport) {
    try {
        const container = document.getElementById('ui-buttons') || document;
        if (!container || container.dataset.bubbleWired === '1') return;
        const logPrefix = '[BubbleUnits] export:';
        const elSvg = document.getElementById('svg_download_link');
        const elPng = document.getElementById('png_download_link');
        const elJson = document.getElementById('json_report_download_link');
        function ts(){ const d=new Date(); return d.toISOString().replace(/[:.]/g,'-'); }
        if (elSvg) {
            elSvg.addEventListener('click', async function(e){
                e.preventDefault();
                // Use the new notify helper function
                notifyViaBridge('Exporting SVG…');
                try {
                    const svgText = cloneSVG('svg.bubbles');
                    if (!svgText) {
                        notifyViaBridge('No SVG found to export yet. Generate the chart first.');
                        throw new Error('no svg');
                    }
                    const b64 = base64FromText(svgText);
                    saveViaBridge('svg', 'phpunit-bubble-report.svg', b64);
                } catch (err) { 
                    console.error(logPrefix, 'svg failed', err);
                    notifyViaBridge('SVG export failed: ' + (err.message || 'unknown error'));
                }
            });
        }
        if (elPng) {
            elPng.addEventListener('click', async function(e){
                e.preventDefault();
                try {
                    // Ensure an SVG exists before attempting export
                    const s = cloneSVG('svg.bubbles');
                    if (!s) {
                        notifyViaBridge('No SVG found to export yet. Generate the chart first.');
                        return;
                    }
                    notifyViaBridge('Exporting PNG…');
                    const b64 = await toPngBase64('svg.bubbles');
                    saveViaBridge('png', 'phpunit-bubble-report.png', b64);
                    elPng.remove();
                } catch (err) { 
                    console.error(logPrefix, 'png failed', err);
                    notifyViaBridge('PNG export failed: ' + (err.message || 'unknown error'));
                }
            });
        }
        if (elJson) {
            elJson.addEventListener('click', function(e){
                e.preventDefault();
                notifyViaBridge('Exporting JSON…');
                try {
                    const text = JSON.stringify(jsonReport);
                    const b64 = base64FromText(text);
                    saveViaBridge('json', 'phpunit-bubble-report.json', b64);
                } catch (err) { 
                    console.error(logPrefix, 'json failed', err);
                    notifyViaBridge('JSON export failed: ' + (err.message || 'unknown error'));
                }
            });
        }
        container.dataset.bubbleWired = '1';
        console.info('[BubbleUnits] snapshot export wiring ready (bubble API=' + (typeof window.__bubble === 'object') + ')');
    } catch (e) { console.error('[BubbleUnits] setupSnapshotExports failed', e); }
}

function generateBubbleGraph(chart, jsonReport) {
    select('#bubbles')
        .datum(jsonReport)
        .call(chart);

    // Prepare browser fallback downloads
    cloneSVGToPNGDownload('svg.bubbles', 'png_download_link');
    cloneSVGToDownload('svg.bubbles', 'svg_download_link');
    cloneJsonDownload(jsonReport, 'json_report_download_link');

    // Wire IDE bridge-based exports if available
    setupSnapshotExports(jsonReport);
}



export async function bubbles() {

    // Compatibility tweaks
    window.URL = window.URL || window.webkitURL;
    const chart = phpUnitBubbles({width: 600, height: 450}).padding(2);

    const junitXmlText = (() => { try { return atob("{{JUNIT_XML_BASE64}}"); } catch (e) { return ""; } })();

    function getJUnitXml() {
        // Safely decode embedded base64 XML provided by the plugin; falls back to empty string if not replaced

        // Keep the same Promise-based flow as fetch().then(t => t.text())
        return Promise.resolve(junitXmlText);
    }

    // Global variables
    const jsonReport = await getJUnitXml()

        /* No longer needed .then(response => response.text())*/
        .then(XMString => {
            // check if the XMString is  actually XML
            //console.log(ReportTransformer.isXMLorHTML(XMString));
            if (ReportTransformer.isXMLorHTML(XMString) === 'XML') {
                //console.log(XMString);
                return ReportTransformer.transform(XMString);
            } else {
                // Quite sure this dependency could be removed and replaced with a fetch() call
                return json('reports/symfony2.json')

            }
        })
        .catch(error => {
            console.log('Error fetching XML file', error)
        })

    // Add global tooltip div
    select('body')
        .append('div')
        .attr('class', 'tooltip w-sm max-w-lg prose')
        .attr('id', 'tooltip')
        .style('position', 'absolute')
        .style('opacity', 0);

    generateBubbleGraph(chart, jsonReport);

    if (import.meta.env.VITE_OUT_DIR !== 'dist') {
        // Update chart with user submitted data
        document.getElementById('report_form').addEventListener('submit', function (e) {

            const chart = phpUnitBubbles({width: 600, height: 450}).padding(2);
            e.preventDefault();

            document.getElementById('sample_introduction').innerText = 'Here is your custom report:';

            let report = document.getElementById('report').value;

            if (report.length > 0) {
                select('#bubbles').datum(ReportTransformer.transform(report)).call(chart);
            }
        });
        // Update the user submitted data from an uploaded xml file
        document.getElementById('file_upload').addEventListener('change', function (event) {
            const file = event.target.files[0];
            if (file) {
                const reader = new FileReader();
                reader.onload = function (e) {
                    document.getElementById('report').value = e.target.result;
                };
                reader.readAsText(file);
            }
        });

    }

    // Authorize PNG report download (for external embedding)
    document.getElementById('bubble_refresh').addEventListener('click', function (e) {
        e.preventDefault();
        generateBubbleGraph(chart, jsonReport);
    });

    // // Authorize PNG report download (for external embedding)
    // document.getElementById('png_download_link').addEventListener('click', function (e) {
    //     if (!confirm('Download the bubble graph as a bitmap (png) image?')) {
    //         e.preventDefault();
    //     }
    // });
    //
    // // Authorize SVG report download (for external embedding)
    // document.getElementById('svg_download_link').addEventListener('click', function (e) {
    //     if (!confirm('Download the bubble graph as a vector (svg) image?')) {
    //         e.preventDefault();
    //     }
    // });
    //
    // // Authorize JSON report download (for external embedding)
    // document.getElementById('json_report_download_link').addEventListener('click', function (e) {
    //     if (!confirm('Download the json object for the bubble graph?')) {
    //         e.preventDefault();
    //     }
    // });
}