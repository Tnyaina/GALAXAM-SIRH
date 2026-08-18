import os
import re

directories = ['frontend/src/components', 'frontend/src/pages']

# Only match if it's surrounded by non-alphanumeric/hyphen (e.g. space, quote)
def make_regex(word):
    return r'(?<![a-zA-Z0-9-])' + word + r'(?![a-zA-Z0-9-])'

remove_words = [
    r'transition-[a-zA-Z0-9-]+',
    r'transition',
    r'duration-[a-zA-Z0-9-]+',
    r'ease-[a-zA-Z0-9-]+',
    r'animate-[a-zA-Z0-9-]+', # this removes animate-spin too, wait!
    r'italic'
]

# We want to keep animate-spin! So let's exclude it.
# Actually, I'll just remove specific animations.
remove_words = [
    r'transition-[a-zA-Z0-9-]+',
    r'transition',
    r'duration-[a-zA-Z0-9-]+',
    r'ease-[a-zA-Z0-9-]+',
    r'animate-fade-in',
    r'animate-pulse',
    r'animate-bounce',
    r'italic'
]

replace_map = {
    make_regex(r'rounded-(sm|md|lg|xl|2xl|3xl|full)'): 'rounded-none',
    make_regex(r'rounded'): 'rounded-none'
}

count = 0

for directory in directories:
    for root, _, files in os.walk(directory):
        for file in files:
            if file.endswith('.js') or file.endswith('.jsx'):
                filepath = os.path.join(root, file)
                
                with open(filepath, 'r') as f:
                    content = f.read()
                
                new_content = content
                for word in remove_words:
                    regex = make_regex(word)
                    new_content = re.sub(regex, '', new_content)
                
                for regex, replacement in replace_map.items():
                    new_content = re.sub(regex, replacement, new_content)
                
                if new_content != content:
                    with open(filepath, 'w') as f:
                        f.write(new_content)
                    count += 1

print(f"Total files cleaned: {count}")
