import { defineConfig, loadEnv } from 'vite'
import tailwindcss from '@tailwindcss/vite'
import { viteSingleFile } from "vite-plugin-singlefile"
import { createHtmlPlugin } from 'vite-plugin-html'
import terser from '@rollup/plugin-terser';


export default ({ mode }) => {
    process.env = { ...process.env, ...loadEnv(mode, process.cwd()) };

    return defineConfig({
        publicDir: 'resources',
        build: {
            outDir: process.env.VITE_OUT_DIR ?? 'dist',
            // IMPORTANT: Preserve literal template placeholders like {{BGColour}} inside CSS.
            // Minifiers/transformers may normalize or drop unknown tokens in CSS values.
            // Disabling CSS minification keeps our placeholders intact for Kotlin-side replacement later.
            cssMinify: true,
            rollupOptions: {
                input: process.env.VITE_OUT_DIR === 'docs' ? 'index.html' : 'PHPUnitBubbleReport.html', // Change this to your new start file
            },
        },
        watch: {
            include: './src/**'
        },
        plugins: [
            tailwindcss(),
            viteSingleFile(),
            createHtmlPlugin({
                minify: {
                    collapseWhitespace: true,
                    removeComments: true,
                    minifyCSS: false,
                },
            }),
            terser({
                output: {
                    comments: false,
                },
            })
        ],
    })
}