import './style.css'
import { bubbles } from './bubbles.js'

let sample = (import.meta.env.VITE_OUT_DIR !== 'docs') ?'':`<div class="prose mx-auto">
        <p id="sample_introduction" class="pl-4">
           The sample above is of the report this utility generates. It is based on the results from a <a href="https://github.com/symfony/symfony/tree/2.3">Symfony 2.3</a> test suite.
           Click here for an example of the default Laravel test suite.
        </p>
    </div>`;

 let app = `
<div class="pt-5 pb-2 mb-12">
    <div id="error-stack-wrapper" class="hidden h-72 overflow-y-scroll my-8">
        <div class="h-full">
            <div id="error-stack" class=" grid grid-cols-1 gap-3 px-2" draggable="true">
            </div>
        </div>
    </div>
    <div class="relative flex items-center justify-center mb-12">
        <div id="bubbles" class="bg-white rounded-4xl p-1 pb-2"></div>
        <div id="ui-buttons">
        
        <a href="#" class="absolute top-2 left-[50%]  ml-[250px] btn text-xs h-8 text-slate-500 hover:text-slate-700 focus-visible:outline-slate-600" id="bubble_refresh"
            title="Re-generate the Bubble Chart (does not re-load the data)">
            <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="1.5" stroke="currentColor" class="size-6">
                <path stroke-linecap="round" stroke-linejoin="round" d="M16.023 9.348h4.992v-.001M2.985 19.644v-4.992m0 0h4.992m-4.993 0 3.181 3.183a8.25 8.25 0 0 0 13.803-3.7M4.031 9.865a8.25 8.25 0 0 1 13.803-3.7l3.181 3.182m0-4.991v4.99" />
            </svg>
        </a>
        <div class="absolute inset-x-0 -bottom-6 flex items-center justify-center">
        <div class="grid grid-cols-3 gap-3">
            <a href="#" class="btn text-xs h-8 text-slate-300/80 bg-slate-600 hover:bg-slate-500 focus-visible:outline-slate-600" id="svg_download_link">&lt;SVG&gt;</a>
            <a href="#" class="btn text-xs h-8 text-slate-300/80 bg-slate-600 hover:bg-slate-500 focus-visible:outline-slate-600" id="json_report_download_link">{json}</a>
            <a href="#" class="btn text-xs text-center h-8 text-slate-300/80 bg-slate-600 hover:bg-slate-500 focus-visible:outline-slate-600" id="png_download_link"><span>.png</span></a>
        </div>
        </div>
        </div>
    </div>
    ` + sample + '</div>';
document.querySelector('#app').innerHTML = app;

bubbles();


