import argparse
from preference_prediction import UserPreferences

def main():
    parser = argparse.ArgumentParser(
        description="instagram_parser scrapes and downloads an instagram user's photos and videos.")
    parser.add_argument('username', help='Instagram user to scrape')
    parser.add_argument('--maximum', '-m', type=int, default=100, help='Maximum number of items to scrape')
    args = parser.parse_args()

    preferences=UserPreferences()
    album=preferences.scrape_instagram(args.username,args.maximum)
    preferences.process_album(album)
    preferences.close()

if __name__ == '__main__':
    main()
