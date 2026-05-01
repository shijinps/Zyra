import os
import logging
from telegram import Update
from telegram.ext import ApplicationBuilder, CommandHandler, MessageHandler, filters, ContextTypes
from agent import process_message
from dotenv import load_dotenv

# Setup basic logging
logging.basicConfig(
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s',
    level=logging.INFO
)

load_dotenv()
TELEGRAM_BOT_TOKEN = os.getenv("TELEGRAM_BOT_TOKEN")

if not TELEGRAM_BOT_TOKEN:
    raise ValueError("TELEGRAM_BOT_TOKEN not set in environment.")

async def start(update: Update, context: ContextTypes.DEFAULT_TYPE):
    """Handles the /start command."""
    await context.bot.send_message(
        chat_id=update.effective_chat.id, 
        text="Hello! I am Zyra-Claw. Send me a bike number and I'll find the details for you."
    )

async def handle_message(update: Update, context: ContextTypes.DEFAULT_TYPE):
    """Handles incoming text messages."""
    user_id = str(update.effective_user.id)
    text = update.message.text
    
    # Send a "typing..." action so the user knows we're processing
    await context.bot.send_chat_action(chat_id=update.effective_chat.id, action='typing')
    
    # Process the message through the Gemini agent
    reply = process_message(user_id, text)
    
    await context.bot.send_message(chat_id=update.effective_chat.id, text=reply)

if __name__ == '__main__':
    # Build the application
    application = ApplicationBuilder().token(TELEGRAM_BOT_TOKEN).build()
    
    # Add handlers
    start_handler = CommandHandler('start', start)
    message_handler = MessageHandler(filters.TEXT & (~filters.COMMAND), handle_message)
    
    application.add_handler(start_handler)
    application.add_handler(message_handler)
    
    # Start polling
    print("Zyra-Claw Telegram Bot is running! Press Ctrl+C to stop.")
    application.run_polling()
