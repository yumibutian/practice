import {defineConfig} from 'vitest/config'
import vue from '@vitejs/plugin-vue'

const inWorkBuddySandbox = Boolean(process.env.CODEBUDDY_SESSION_ID)


const UNI_ELEMENTS = [
    'view', 'text', 'scroll-view', 'swiper', 'swiper-item', 'image', 'input',
    'textarea', 'button', 'navigator', 'picker', 'picker-view', 'picker-view-column',
    'checkbox', 'checkbox-group', 'radio', 'radio-group', 'switch', 'slider',
    'progress', 'rich-text', 'video', 'audio', 'canvas', 'form', 'label', 'icon'
]

export default defineConfig({
    plugins: [
        vue({
            template: {
                compilerOptions: {
                    isCustomElement: (tag) => UNI_ELEMENTS.includes(tag),
                    hoistStatic: false
                }
            }
        })
    ],
    test: {
        environment: 'node',
        globals: true,
        fileParallelism: !inWorkBuddySandbox,
        testTransformMode: {
            web: [
                '**/test/practice-submit.test.js',
                '**/test/practice-instant-judge.test.js',
                '**/test/practice-resume.test.js',
                '**/test/exam-config.test.js',
                '**/test/import-preview.test.js',
                '**/test/import-select.test.js',
                '**/test/wrong-book.test.js',
                '**/test/wrong-bank.test.js',
                '**/test/mine.test.js',
                '**/test/mine-storage.test.js',
                '**/test/mine-legal.test.js',
                '**/test/mine-feedback.test.js',
                '**/test/report-type-label.test.js'
            ]
        }
    }
})
