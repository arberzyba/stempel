// Takes the README screenshots and the demo GIF from a running Stempel.
// 1. docker compose up --build   (in the project root)
// 2. npm run docs:capture        (in frontend/; BASE_URL defaults to http://localhost:8080)
import { mkdir, writeFile } from 'node:fs/promises'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import gifenc from 'gifenc' // an older CommonJS package: no named imports
import { chromium } from 'playwright'
import { PNG } from 'pngjs'

const { applyPalette, GIFEncoder, quantize } = gifenc

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..')
const docs = path.join(root, 'docs')
const validPdf = path.join(root, 'backend/src/test/resources/samples/EN16931_Einfach.pdf')
const brokenXml = path.join(docs, 'demo/rechnung-mit-fehler.xml')
const baseUrl = process.env.BASE_URL ?? 'http://localhost:8080'

await mkdir(docs, { recursive: true })
const browser = await chromium.launch()
const page = await browser.newPage({ viewport: { width: 1280, height: 800 }, locale: 'de-DE' })

/** Each GIF frame is a screenshot plus how long it stays visible. */
const frames = []
async function frame(delayMs) {
  frames.push({ png: await page.screenshot(), delayMs })
}
const save = (name, options = {}) => page.screenshot({ path: path.join(docs, name), ...options })
const settle = () => page.waitForTimeout(450) // let the stamp animation finish

// 1. Upload screen
await page.goto(baseUrl)
await page.evaluate(() => document.fonts.ready)
await frame(1800)
await save('screenshot-upload.png')

// 2. A valid ZUGFeRD PDF: the invoice appears first, the check fills in after.
await page.setInputFiles('input[type="file"]', validPdf)
await page.getByText('Wird geprüft').waitFor({ timeout: 5000 }).then(() => frame(900), () => {})
await page.getByText('Gültig', { exact: true }).waitFor({ timeout: 60000 })
await settle()
await frame(2800)
await save('screenshot-valid.png', { fullPage: true })

// 3. An invoice with a wrong total, and the error highlighted in the document.
await page.getByRole('button', { name: 'Neue Datei' }).click()
await frame(700)
await page.setInputFiles('input[type="file"]', brokenXml)
await page.getByText('Nicht gültig', { exact: true }).waitFor({ timeout: 60000 })
await settle()
await frame(2200)
await page.getByRole('button', { name: /Der Gesamtbetrag stimmt nicht/ }).click()
await page.waitForTimeout(800) // smooth scroll to the totals
await frame(3500)
await save('screenshot-errors.png')

await browser.close()

// GIF: reduce each frame to 256 colors (fine for a mostly black-and-white UI).
const gif = GIFEncoder()
for (const { png, delayMs } of frames) {
  const { data, width, height } = PNG.sync.read(png)
  const palette = quantize(data, 256)
  gif.writeFrame(applyPalette(data, palette), width, height, { palette, delay: delayMs })
}
gif.finish()
await writeFile(path.join(docs, 'stempel-demo.gif'), gif.bytes())

console.log(`Saved ${frames.length} GIF frames and 3 screenshots to ${docs}`)
